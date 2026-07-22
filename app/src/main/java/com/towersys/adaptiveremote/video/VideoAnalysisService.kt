package com.towersys.adaptiveremote.video

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.towersys.adaptiveremote.MainActivity
import com.towersys.adaptiveremote.R
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.device.control.KnightConnectionStatus
import com.towersys.adaptiveremote.device.control.KnightControlService
import com.towersys.adaptiveremote.device.control.KnightControlState
import com.towersys.adaptiveremote.device.control.VideoMonitorState
import com.towersys.adaptiveremote.device.control.VideoMonitorStatus
import com.towersys.adaptiveremote.text.SecretStore
import com.towersys.adaptiveremote.text.TextMonitorOverlay
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlin.math.max

class VideoAnalysisService : Service() {
    private data class CapturedFrame(val jpegBase64: String, val motionSignature: IntArray)
    private data class FrameCluster(val encodedFrames: List<String>, val motionScore: Float)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clusters = Channel<FrameCluster>(Channel.CONFLATED)
    private var captureJob: Job? = null
    private var analysisJob: Job? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlay: TextMonitorOverlay? = null
    private var cleaningUp = false
    private var previousExplicitState: Boolean? = null
    private val recentHistory = ArrayDeque<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (KnightControlState.connection.value !is KnightConnectionStatus.Ready) {
            VideoMonitorState.status.value = VideoMonitorStatus.Error("Connect a compatible device before starting Video mode.")
            stopSelf()
            return
        }
        val apiKey = SecretStore(this).loadApiKey()
        if (apiKey.isBlank()) {
            VideoMonitorState.status.value = VideoMonitorStatus.Error("Save an xAI API key in Text mode first.")
            stopSelf()
            return
        }
        val captureData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CAPTURE_DATA)
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (captureData == null || resultCode != Activity.RESULT_OK) {
            VideoMonitorState.status.value = VideoMonitorStatus.Error("Screen-capture permission was not granted.")
            stopSelf()
            return
        }
        startProjectionForeground()
        AiIntensitySettings.initialize(this)
        startService(Intent(this, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP_TEXT_MONITOR))
        startService(Intent(this, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP_PROCEDURAL))
        stopKnightOutput()
        overlay = TextMonitorOverlay(this, stopLabel = "STOP VIDEO") {
            startService(Intent(this, VideoAnalysisService::class.java).setAction(ACTION_STOP))
        }.also {
            if (!it.show()) {
                VideoMonitorState.status.value = VideoMonitorStatus.Error("Floating-control permission is required.")
                stopCapture()
                return
            }
            it.update("Video • starting", "Preparing five-frame capture", 0f)
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, captureData)
        if (mediaProjection == null) {
            VideoMonitorState.status.value = VideoMonitorStatus.Error("Android could not start screen capture.")
            stopCapture()
            return
        }
        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!cleaningUp) stopCapture(releaseProjection = false)
            }
        }, null)
        createVirtualDisplay()
        startLoops(apiKey)
    }

    private fun createVirtualDisplay() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            3,
        )
        virtualDisplay = projection?.createVirtualDisplay(
            "AdaptiveRemoteVideo",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )
    }

    private fun startLoops(apiKey: String) {
        captureJob = scope.launch {
            while (isActive) {
                VideoMonitorState.status.value = VideoMonitorStatus.Capturing
                overlay?.update("Video • capturing", "Frames 0/5 over one second", 0f)
                val frames = mutableListOf<CapturedFrame>()
                repeat(VideoSamplingPolicy.FRAME_COUNT) { index ->
                    captureFrame()?.let(frames::add)
                    overlay?.update(
                        "Video • capturing",
                        "Frames ${index + 1}/5 over one second",
                        (index + 1f) / VideoSamplingPolicy.FRAME_COUNT,
                    )
                    if (index < VideoSamplingPolicy.FRAME_COUNT - 1) delay(VideoSamplingPolicy.FRAME_INTERVAL_MS)
                }
                if (frames.size == VideoSamplingPolicy.FRAME_COUNT) {
                    clusters.trySend(
                        FrameCluster(
                            encodedFrames = frames.map { it.jpegBase64 },
                            motionScore = calculateMotionScore(frames),
                        ),
                    )
                }
                else {
                    VideoMonitorState.status.value = VideoMonitorStatus.Error("Could not capture all five frames.")
                }
                delay(CLUSTER_INTERVAL_MS)
            }
        }
        analysisJob = scope.launch {
            val client = GrokVideoClient()
            for (cluster in clusters) {
                VideoMonitorState.status.value = VideoMonitorStatus.Analyzing
                overlay?.update("Video • analyzing", "Five frames sent as one ordered cluster", 0f)
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        client.interpret(apiKey, cluster.encodedFrames, recentHistory.toList())
                    }
                }
                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.message ?: "Video analysis failed."
                    VideoMonitorState.status.value = VideoMonitorStatus.Error(message)
                    overlay?.update("Video • error", message.take(82), 0f)
                    continue
                }
                val interpretation = result.getOrThrow()
                val replaceImmediately = previousExplicitState != null &&
                    previousExplicitState != interpretation.explicitContentPresent
                previousExplicitState = interpretation.explicitContentPresent
                recentHistory.addLast(interpretation.summary)
                while (recentHistory.size > 3) recentHistory.removeFirst()
                VideoMonitorState.status.value = VideoMonitorStatus.Playing(interpretation.summary)
                val motionAmplifier = motionAmplifier(cluster.motionScore)
                val multiplier = AiIntensitySettings.multiplier.value
                val levels = interpretation.pattern.steps.map { step ->
                    (step.intensity * multiplier * motionAmplifier * 255f / 100f).toInt().coerceIn(0, 255)
                }.toIntArray()
                val motionPercent = (cluster.motionScore * 100).toInt()
                overlay?.update(
                    if (levels.any { it > 0 }) "Video • responding" else "Video • output off",
                    if (interpretation.explicitContentPresent) {
                        "Explicit: YES • Motion $motionPercent% • ${interpretation.summary}".take(82)
                    } else {
                        "Explicit: NO • Motion $motionPercent% • output off"
                    },
                    0f,
                )
                startService(
                    Intent(this@VideoAnalysisService, KnightControlService::class.java)
                        .setAction(KnightControlService.ACTION_QUEUE_AI_BATCH)
                        .putExtra(KnightControlService.EXTRA_PATTERN_NAME, interpretation.pattern.name)
                        .putExtra(KnightControlService.EXTRA_PATTERN_LEVELS, levels)
                        .putExtra(
                            KnightControlService.EXTRA_PATTERN_DURATIONS,
                            interpretation.pattern.steps.map { it.durationMs }.toLongArray(),
                        )
                        .putExtra(KnightControlService.EXTRA_PATTERN_REPEATS, 1)
                        .putExtra(KnightControlService.EXTRA_BLEND_FROM_CURRENT, true)
                        .putExtra(KnightControlService.EXTRA_SMOOTH_TRANSITIONS, true)
                        .putExtra(KnightControlService.EXTRA_HOLD_FINAL_LEVEL, true)
                        .putExtra(KnightControlService.EXTRA_REPLACE_IMMEDIATELY, replaceImmediately),
                )
            }
        }
    }

    private suspend fun captureFrame(): CapturedFrame? {
        repeat(10) {
            imageReader?.acquireLatestImage()?.let { image ->
                return withContext(Dispatchers.Default) { image.toCapturedFrame() }
            }
            delay(50)
        }
        return null
    }

    private fun Image.toCapturedFrame(): CapturedFrame = use { image ->
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val paddedWidth = width + rowPadding / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (cropped !== padded) padded.recycle()
        val scale = (MAX_FRAME_EDGE.toFloat() / max(width, height)).coerceAtMost(1f)
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(cropped, (width * scale).toInt(), (height * scale).toInt(), true)
                .also { cropped.recycle() }
        } else cropped
        val bytes = ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        val signatureBitmap = Bitmap.createScaledBitmap(
            output,
            MOTION_SAMPLE_SIZE,
            MOTION_SAMPLE_SIZE,
            true,
        )
        val pixels = IntArray(MOTION_SAMPLE_SIZE * MOTION_SAMPLE_SIZE)
        signatureBitmap.getPixels(
            pixels,
            0,
            MOTION_SAMPLE_SIZE,
            0,
            0,
            MOTION_SAMPLE_SIZE,
            MOTION_SAMPLE_SIZE,
        )
        val signature = pixels.map { pixel ->
            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff
            (red * 30 + green * 59 + blue * 11) / 100
        }.toIntArray()
        signatureBitmap.recycle()
        output.recycle()
        CapturedFrame(Base64.encodeToString(bytes, Base64.NO_WRAP), signature)
    }

    private fun calculateMotionScore(frames: List<CapturedFrame>): Float {
        if (frames.size < 2) return 0f
        val meanDifference = frames.zipWithNext().map { (first, second) ->
            first.motionSignature.indices.sumOf { index ->
                kotlin.math.abs(first.motionSignature[index] - second.motionSignature[index])
            }.toFloat() / first.motionSignature.size / 255f
        }.average().toFloat()
        return ((meanDifference - MOTION_NOISE_FLOOR) / MOTION_FULL_SCALE_RANGE).coerceIn(0f, 1f)
    }

    private fun motionAmplifier(motionScore: Float): Float =
        MIN_MOTION_AMPLIFIER + motionScore.coerceIn(0f, 1f) * MOTION_AMPLIFIER_RANGE

    private fun startProjectionForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopCapture(releaseProjection: Boolean = true) {
        if (cleaningUp) return
        cleaningUp = true
        captureJob?.cancel()
        analysisJob?.cancel()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        if (releaseProjection) runCatching { projection?.stop() }
        projection = null
        overlay?.remove()
        overlay = null
        VideoMonitorState.status.value = VideoMonitorStatus.Idle
        stopKnightOutput()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Video analysis", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stopKnightOutput() {
        startService(Intent(this, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP))
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Adaptive Remote • Video")
        .setContentText("Capturing five-frame clusters for live analysis")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "STOP VIDEO",
            PendingIntent.getService(
                this,
                2,
                Intent(this, VideoAnalysisService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        const val ACTION_START = "com.towersys.adaptiveremote.action.START_VIDEO"
        const val ACTION_STOP = "com.towersys.adaptiveremote.action.STOP_VIDEO"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_CAPTURE_DATA = "capture_data"
        private const val CHANNEL_ID = "video_analysis"
        private const val NOTIFICATION_ID = 4102
        private const val CLUSTER_INTERVAL_MS = 1_000L
        private const val MAX_FRAME_EDGE = 512
        private const val JPEG_QUALITY = 45
        private const val MOTION_SAMPLE_SIZE = 16
        private const val MOTION_NOISE_FLOOR = 0.01f
        private const val MOTION_FULL_SCALE_RANGE = 0.10f
        private const val MIN_MOTION_AMPLIFIER = 1.25f
        private const val MOTION_AMPLIFIER_RANGE = 2.25f
    }
}

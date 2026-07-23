package com.towersys.adaptiveremote.device.control

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.towersys.adaptiveremote.MainActivity
import com.towersys.adaptiveremote.R
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.device.protocol.BleProtocolAdapter
import com.towersys.adaptiveremote.device.protocol.DeviceCapability
import com.towersys.adaptiveremote.device.protocol.DeviceProtocolRegistry
import com.towersys.adaptiveremote.patterns.PatternRepeatPolicy
import com.towersys.adaptiveremote.patterns.PatternStep
import com.towersys.adaptiveremote.procedural.GrokProceduralClient
import com.towersys.adaptiveremote.procedural.ProceduralBatch
import com.towersys.adaptiveremote.procedural.ProceduralOverlay
import com.towersys.adaptiveremote.procedural.ProceduralStyle
import com.towersys.adaptiveremote.text.GrokTextClient
import com.towersys.adaptiveremote.text.SecretStore
import com.towersys.adaptiveremote.text.TextMonitorOverlay
import com.towersys.adaptiveremote.text.VisibleTextState
import kotlin.random.Random

class KnightControlService : Service() {
    private data class AiBatch(val name: String, val levels: IntArray, val durations: LongArray)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var patternJob: Job? = null
    private var patternGeneration = 0L
    @Volatile private var aiQueueJob: Job? = null
    private var aiQueueGeneration = 0L
    @Volatile private var currentAiBatch: AiBatch? = null
    @Volatile private var pendingAiBatch: AiBatch? = null
    private var aiFallbackLoops = 0
    @Volatile private var aiInFallback = false
    private var reconnectJob: Job? = null
    private var textMonitorJob: Job? = null
    private var textMonitorOverlay: TextMonitorOverlay? = null
    private var proceduralJob: Job? = null
    private var proceduralOverlay: ProceduralOverlay? = null
    private var proceduralSessionStartedAt = 0L
    private var proceduralBatchNumber = 1
    private var openingTeasesUsed = 0
    private var consecutiveTeases = 0
    private var proceduralFinishing = false
    private var currentTextSnippet = "Waiting for visible text"
    private var userRequestedDisconnect = false
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var currentDevice: KnownDevice? = null
    private var currentProtocol: BleProtocolAdapter? = null

    override fun onCreate() {
        super.onCreate()
        AiIntensitySettings.initialize(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Device"
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val protocolId = intent.getStringExtra(EXTRA_PROTOCOL_ID)
                when {
                    address == null -> rejectConnection("Missing device Bluetooth address.")
                    protocolId == null -> rejectConnection("Missing device protocol identifier.")
                    else -> {
                        userRequestedDisconnect = false
                        startForeground(NOTIFICATION_ID, buildNotification("Connecting to $name…"))
                        connect(KnownDevice(name, address, protocolId))
                    }
                }
            }
            ACTION_SET_LEVEL -> {
                clearAiQueue()
                patternGeneration++
                patternJob?.cancel()
                patternJob = null
                DeviceControlState.patternPlayback.value = PatternPlaybackState.Idle
                setLevel(intent.getIntExtra(EXTRA_LEVEL, 0))
            }
            ACTION_PLAY_PATTERN -> playPattern(intent)
            ACTION_QUEUE_AI_BATCH -> queueAiBatch(intent)
            ACTION_START_TEXT_MONITOR -> startTextMonitor()
            ACTION_STOP_TEXT_MONITOR -> stopTextMonitor()
            ACTION_START_PROCEDURAL -> startProcedural()
            ACTION_PROCEDURAL_CLOSE -> handleProceduralClose()
            ACTION_STOP_PROCEDURAL -> stopProcedural()
            ACTION_STOP -> {
                cancelProceduralUi()
                stopOutput()
            }
            ACTION_DISCONNECT -> {
                userRequestedDisconnect = true
                shutdown()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: KnownDevice) {
        if (!hasBluetoothPermissions()) {
            fail("Nearby-device permission is missing.")
            return
        }
        val protocol = DeviceProtocolRegistry.findById(device.protocolId)
        if (protocol == null) {
            rejectConnection("The saved device protocol is not supported by this build.")
            return
        }
        reconnectJob?.cancel()
        closeGattWithoutDisconnect()
        currentDevice = device
        currentProtocol = protocol
        DeviceControlState.connection.value = DeviceConnectionStatus.Connecting(device)
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            scheduleReconnect("Bluetooth is off; waiting to reconnect…")
            return
        }
        runCatching {
            gatt = adapter.getRemoteDevice(device.address)
                .connectGatt(this, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        }.onFailure { scheduleReconnect(it.message ?: "Could not connect to the device.") }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runCatching { connection.close() }
                if (gatt === connection) gatt = null
                scheduleReconnect("Device connection interrupted (status $status).")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> if (!connection.discoverServices()) {
                    fail("Could not discover supported device controls.")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    DeviceControlState.outputLevel.value = 0
                    runCatching { connection.close() }
                    if (gatt === connection) {
                        gatt = null
                        writeCharacteristic = null
                    }
                    if (userRequestedDisconnect) {
                        DeviceControlState.connection.value = DeviceConnectionStatus.Disconnected
                    } else {
                        scheduleReconnect("Device disconnected; reconnecting…")
                    }
                }
            }
        }

        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                scheduleReconnect("Device control discovery failed; reconnecting…")
                return
            }
            val protocol = currentProtocol
            writeCharacteristic = protocol?.let {
                connection
                .getService(protocol.serviceUuid)
                ?.getCharacteristic(protocol.writeCharacteristicUuid)
            }
            val device = currentDevice
            if (writeCharacteristic == null || device == null) {
                scheduleReconnect("Device control channel unavailable; reconnecting…")
                return
            }
            DeviceControlState.connection.value = DeviceConnectionStatus.Ready(device)
            updateNotification("Connected to ${device.name} • output 0%")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setLevel(requestedLevel: Int) {
        val level = requestedLevel.coerceIn(0, 255)
        val connection = gatt ?: return
        val characteristic = writeCharacteristic ?: return
        val protocol = currentProtocol ?: return
        val command = if (level == 0) {
            protocol.encodeStop()
        } else {
            protocol.encodeScalar(DeviceCapability.OSCILLATION, level)
        }
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connection.writeCharacteristic(
                characteristic,
                command,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = command
            @Suppress("DEPRECATION")
            connection.writeCharacteristic(characteristic)
        }
        if (accepted) {
            DeviceControlState.outputLevel.value = level
            val percent = (level * 100f / 255f).toInt()
            updateNotification("Connected to ${currentDevice?.name ?: "device"} • output $percent%")
        } else {
            DeviceControlState.connection.value = DeviceConnectionStatus.Error(
                "Android rejected the control command. Press Stop, then reconnect.",
            )
        }
    }

    private fun playPattern(intent: Intent) {
        clearAiQueue()
        val levels = intent.getIntArrayExtra(EXTRA_PATTERN_LEVELS) ?: return
        val durations = intent.getLongArrayExtra(EXTRA_PATTERN_DURATIONS) ?: return
        if (levels.isEmpty() || levels.size != durations.size ||
            DeviceControlState.connection.value !is DeviceConnectionStatus.Ready
        ) return
        val name = intent.getStringExtra(EXTRA_PATTERN_NAME) ?: "Pattern"
        val repeats = intent.getIntExtra(EXTRA_PATTERN_REPEATS, 1).coerceIn(0, 100)
        val shouldBlend = intent.getBooleanExtra(EXTRA_BLEND_FROM_CURRENT, false)
        val smoothTransitions = intent.getBooleanExtra(EXTRA_SMOOTH_TRANSITIONS, false)
        val holdFinalLevel = intent.getBooleanExtra(EXTRA_HOLD_FINAL_LEVEL, false)
        val startingLevel = DeviceControlState.outputLevel.value
        val generation = ++patternGeneration
        patternJob?.cancel()
        patternJob = serviceScope.launch {
            var completedNaturally = false
            try {
                if (shouldBlend) blendTo(startingLevel, levels.first())
                var cycle = 0
                while (PatternRepeatPolicy.shouldStartCycle(repeats, cycle)) {
                    levels.indices.forEach { index ->
                        DeviceControlState.patternPlayback.value = PatternPlaybackState.Playing(
                            name = name,
                            step = index + 1,
                            totalSteps = levels.size,
                        )
                        if (holdFinalLevel) {
                            textMonitorOverlay?.update(
                                titleText = "Text • step ${index + 1}/${levels.size}",
                                detailText = currentTextSnippet,
                                progressFraction = (index + 1f) / levels.size,
                            )
                        }
                        val duration = durations[index].coerceIn(100L, 60_000L)
                        if (smoothTransitions) {
                            blendStepTo(levels[index], duration)
                        } else {
                            setLevel(levels[index])
                            delay(duration)
                        }
                    }
                    cycle++
                }
                completedNaturally = true
            } finally {
                if (completedNaturally && generation == patternGeneration) {
                    if (!holdFinalLevel) {
                        setLevel(0)
                        DeviceControlState.patternPlayback.value = PatternPlaybackState.Idle
                    } else {
                        textMonitorOverlay?.update(
                            titleText = "Caught up • scroll",
                            detailText = currentTextSnippet,
                            progressFraction = 1f,
                        )
                    }
                }
            }
        }
    }

    private fun queueAiBatch(intent: Intent) {
        val levels = intent.getIntArrayExtra(EXTRA_PATTERN_LEVELS) ?: return
        val durations = intent.getLongArrayExtra(EXTRA_PATTERN_DURATIONS) ?: return
        if (levels.isEmpty() || levels.size != durations.size ||
            DeviceControlState.connection.value !is DeviceConnectionStatus.Ready
        ) return
        val batch = AiBatch(
            name = intent.getStringExtra(EXTRA_PATTERN_NAME) ?: "AI batch",
            levels = levels.map { it.coerceIn(0, 255) }.toIntArray(),
            durations = durations.map { it.coerceIn(100L, 60_000L) }.toLongArray(),
        )
        val replaceImmediately = intent.getBooleanExtra(EXTRA_REPLACE_IMMEDIATELY, false)
        if (aiQueueJob == null || replaceImmediately || aiInFallback) {
            startAiQueue(batch)
        } else {
            pendingAiBatch = batch
        }
    }

    private fun startAiQueue(first: AiBatch) {
        patternGeneration++
        patternJob?.cancel()
        patternJob = null
        aiQueueJob?.cancel()
        val queueGeneration = ++aiQueueGeneration
        currentAiBatch = first
        pendingAiBatch = null
        aiFallbackLoops = 0
        aiInFallback = false
        aiQueueJob = serviceScope.launch {
            var batch = first
            try {
                while (isActive) {
                    currentAiBatch = batch
                    playAiBatch(batch)
                    val next = pendingAiBatch
                    if (next != null) {
                        pendingAiBatch = null
                        aiFallbackLoops = 0
                        aiInFallback = false
                        batch = next
                    } else {
                        aiFallbackLoops++
                        if (aiFallbackLoops > MAX_AI_FALLBACK_LOOPS) {
                            setLevel(0)
                            DeviceControlState.patternPlayback.value = PatternPlaybackState.Idle
                            textMonitorOverlay?.update("AI generation delayed", "Stopped after three fallback loops", 0f)
                            break
                        }
                        aiInFallback = true
                        textMonitorOverlay?.update(
                            "Fallback • loop $aiFallbackLoops/$MAX_AI_FALLBACK_LOOPS",
                            "Waiting for the next Grok batch",
                            0f,
                        )
                    }
                }
            } finally {
                aiInFallback = false
                if (queueGeneration == aiQueueGeneration) {
                    aiQueueJob = null
                }
            }
        }
    }

    private suspend fun playAiBatch(batch: AiBatch) {
        blendTo(DeviceControlState.outputLevel.value, batch.levels.first())
        batch.levels.indices.forEach { index ->
            DeviceControlState.patternPlayback.value = PatternPlaybackState.Playing(
                batch.name,
                index + 1,
                batch.levels.size,
            )
            textMonitorOverlay?.update(
                batch.name,
                currentTextSnippet,
                (index + 1f) / batch.levels.size,
            )
            blendStepTo(batch.levels[index], batch.durations[index])
        }
    }

    private fun clearAiQueue() {
        aiQueueGeneration++
        aiQueueJob?.cancel()
        aiQueueJob = null
        currentAiBatch = null
        pendingAiBatch = null
        aiFallbackLoops = 0
        aiInFallback = false
    }

    private suspend fun blendTo(from: Int, to: Int) {
        if (from == to) return
        repeat(BLEND_STEPS) { index ->
            val fraction = (index + 1).toFloat() / BLEND_STEPS
            setLevel((from + (to - from) * fraction).toInt())
            delay(BLEND_STEP_DELAY_MS)
        }
    }

    private suspend fun blendStepTo(to: Int, durationMs: Long) {
        val from = DeviceControlState.outputLevel.value
        val blendDuration = minOf(AI_STEP_BLEND_DURATION_MS, durationMs / 2)
        if (from == to || blendDuration < AI_STEP_BLEND_INTERVAL_MS) {
            setLevel(to)
            delay(durationMs)
            return
        }
        val steps = (blendDuration / AI_STEP_BLEND_INTERVAL_MS).toInt().coerceAtLeast(2)
        repeat(steps) { index ->
            val fraction = (index + 1f) / steps
            setLevel((from + (to - from) * fraction).toInt())
            delay(blendDuration / steps)
        }
        delay((durationMs - blendDuration).coerceAtLeast(0L))
    }

    private fun startTextMonitor() {
        if (DeviceControlState.connection.value !is DeviceConnectionStatus.Ready) {
            TextMonitorState.status.value = TextMonitorStatus.Error("A compatible device is not connected.")
            return
        }
        val apiKey = SecretStore(this).loadApiKey()
        if (apiKey.isBlank()) {
            TextMonitorState.status.value = TextMonitorStatus.Error("Save an xAI API key first.")
            return
        }
        cancelProceduralUi()
        stopOutput()
        textMonitorJob?.cancel()
        textMonitorOverlay?.remove()
        textMonitorOverlay = TextMonitorOverlay(this) {
            startService(Intent(this, KnightControlService::class.java).setAction(ACTION_STOP_TEXT_MONITOR))
        }.also { overlay ->
            if (!overlay.show()) {
                TextMonitorState.status.value = TextMonitorStatus.Error("Floating-control permission is required.")
                return
            }
        }
        textMonitorOverlay?.update("Text • ready", "Switch to your reading app", 0f)
        textMonitorJob = serviceScope.launch {
            val client = GrokTextClient()
            var lastAnalyzed = ""
            var previousGate: Boolean? = null
            val recentHistory = ArrayDeque<String>()
            TextMonitorState.status.value = TextMonitorStatus.WaitingForText
            while (isActive) {
                val candidate = VisibleTextState.latestText.value.trim()
                if (candidate.isBlank()) {
                    delay(TEXT_POLL_DELAY_MS)
                    continue
                }
                val sourceChanged = candidate != lastAnalyzed
                if (!sourceChanged && pendingAiBatch != null) {
                    delay(TEXT_POLL_DELAY_MS)
                    continue
                }
                if (sourceChanged) {
                    delay(TEXT_STABILITY_DELAY_MS)
                    if (candidate != VisibleTextState.latestText.value.trim()) continue
                }
                currentTextSnippet = candidate.replace(Regex("\\s+"), " ").take(TEXT_SNIPPET_LENGTH)
                textMonitorOverlay?.update(
                    titleText = "Analyzing new text",
                    detailText = currentTextSnippet,
                    progressFraction = 0f,
                )
                TextMonitorState.status.value = TextMonitorStatus.Analyzing(VisibleTextState.sourceApp.value)
                val interpretationResult = runCatching {
                    withContext(Dispatchers.IO) {
                        client.interpret(
                            apiKey,
                            candidate,
                            targetDurationMs = 20_000L,
                            recentHistory = recentHistory.toList(),
                            boundedBatch = true,
                        )
                    }
                }
                if (interpretationResult.isFailure) {
                    TextMonitorState.status.value = TextMonitorStatus.Error(
                        interpretationResult.exceptionOrNull()?.message ?: "Continuous analysis failed.",
                    )
                    delay(TEXT_RETRY_DELAY_MS)
                    continue
                }
                val interpretation = interpretationResult.getOrThrow()
                lastAnalyzed = candidate
                val replaceImmediately = previousGate != null && previousGate != interpretation.hasActiveSteps
                previousGate = interpretation.hasActiveSteps
                recentHistory.addLast(interpretation.summary)
                while (recentHistory.size > 3) recentHistory.removeFirst()
                TextMonitorState.status.value = TextMonitorStatus.Playing(interpretation.summary)
                textMonitorOverlay?.update(
                    titleText = "Response ready",
                    detailText = interpretation.summary.take(TEXT_SNIPPET_LENGTH),
                    progressFraction = 0f,
                )
                val levels = interpretation.pattern.steps.map { step ->
                    (step.intensity * AiIntensitySettings.multiplier.value * 255f / 100f).toInt().coerceIn(0, 255)
                }.toIntArray()
                queueAiBatch(
                    Intent().putExtra(EXTRA_PATTERN_NAME, interpretation.pattern.name)
                        .putExtra(EXTRA_PATTERN_LEVELS, levels)
                        .putExtra(EXTRA_PATTERN_DURATIONS, interpretation.pattern.steps.map { it.durationMs }.toLongArray())
                        .putExtra(EXTRA_PATTERN_REPEATS, 1)
                        .putExtra(EXTRA_BLEND_FROM_CURRENT, true)
                        .putExtra(EXTRA_SMOOTH_TRANSITIONS, true)
                        .putExtra(EXTRA_HOLD_FINAL_LEVEL, true)
                        .putExtra(EXTRA_REPLACE_IMMEDIATELY, replaceImmediately),
                )
                TextMonitorState.status.value = TextMonitorStatus.WaitingForText
            }
        }
        updateNotification("Continuous Text mode active • tap STOP for output")
    }

    private fun stopTextMonitor() {
        textMonitorJob?.cancel()
        textMonitorJob = null
        textMonitorOverlay?.remove()
        textMonitorOverlay = null
        TextMonitorState.status.value = TextMonitorStatus.Idle
        stopOutput()
        updateNotification("Connected to ${currentDevice?.name ?: "device"} • output 0%")
    }

    private fun startProcedural() {
        if (DeviceControlState.connection.value !is DeviceConnectionStatus.Ready) {
            ProceduralMonitorState.status.value = ProceduralMonitorStatus.Error("A compatible device is not connected.")
            return
        }
        val apiKey = SecretStore(this).loadApiKey()
        if (apiKey.isBlank()) {
            ProceduralMonitorState.status.value = ProceduralMonitorStatus.Error("Save an xAI API key first.")
            return
        }
        stopTextMonitor()
        cancelProceduralUi()
        stopOutput()
        proceduralSessionStartedAt = System.currentTimeMillis()
        proceduralBatchNumber = 1
        openingTeasesUsed = 0
        consecutiveTeases = 0
        proceduralFinishing = false
        proceduralOverlay = ProceduralOverlay(
            this,
            onClose = { startService(Intent(this, KnightControlService::class.java).setAction(ACTION_PROCEDURAL_CLOSE)) },
            onStop = { startService(Intent(this, KnightControlService::class.java).setAction(ACTION_STOP_PROCEDURAL)) },
        ).also {
            if (!it.show()) {
                ProceduralMonitorState.status.value = ProceduralMonitorStatus.Error("Floating-control permission is required.")
                return
            }
            it.update("Procedural • starting", "Grok is composing batch 1")
        }
        ProceduralMonitorState.status.value = ProceduralMonitorStatus.Starting
        proceduralJob = serviceScope.launch { runProceduralLoop(apiKey) }
    }

    private suspend fun runProceduralLoop(apiKey: String, forcedFirstStyle: ProceduralStyle? = null) {
        val client = GrokProceduralClient()
        val history = ArrayDeque<String>()
        var forcedStyle = forcedFirstStyle
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val batchNumber = proceduralBatchNumber
            val allowed = ProceduralStyle.entries.toMutableSet().apply {
                if (consecutiveTeases >= 2 || (batchNumber <= 8 && openingTeasesUsed >= 4)) {
                    remove(ProceduralStyle.TEASE)
                }
            }
            proceduralOverlay?.update(
                "Procedural • generating",
                "Batch $batchNumber • next Grok batch",
            )
            val requestedStyle = forcedStyle
            forcedStyle = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    client.generate(
                        apiKey = apiKey,
                        allowedStyles = allowed,
                        recentHistory = history.toList(),
                        batchNumber = batchNumber,
                        openingTeasesRemaining = if (batchNumber <= 8) 4 - openingTeasesUsed else 0,
                        forcedStyle = requestedStyle,
                        specialInstruction = if (batchNumber <= 8 && openingTeasesUsed < 4) {
                            "While opening Tease credits remain, give TEASE approximately 60% selection weight."
                        } else null,
                    )
                }
            }
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "Procedural generation failed."
                ProceduralMonitorState.status.value = ProceduralMonitorStatus.Error(message)
                proceduralOverlay?.update("Procedural • retrying", message.take(82))
                delay(PROCEDURAL_RETRY_MS)
                continue
            }
            val batch = result.getOrThrow()
            if (batch.style == ProceduralStyle.TEASE) {
                consecutiveTeases++
                if (batchNumber <= 8) openingTeasesUsed++
            } else consecutiveTeases = 0
            history.addLast("${batch.style}: ${batch.summary}")
            while (history.size > 3) history.removeFirst()

            // Keep Grok one generation ahead of the bounded playback queue. The
            // completed batch remains local to this coroutine until the single
            // pending slot opens, so playback is still current + one pending.
            // Previously generation did not begin until that slot opened at a
            // batch boundary, which made slow responses visible as gaps.
            if (pendingAiBatch != null && aiQueueJob != null) {
                proceduralOverlay?.update(
                    "${batch.style.name.lowercase().replaceFirstChar { it.uppercase() }} • batch $batchNumber ready",
                    "Waiting for the playback queue • ${openingTeasesRemaining()} opening Tease left",
                )
            }
            while (pendingAiBatch != null && aiQueueJob != null &&
                kotlinx.coroutines.currentCoroutineContext().isActive
            ) {
                delay(PROCEDURAL_QUEUE_POLL_MS)
            }
            queueProceduralBatch(batch, replaceImmediately = aiQueueJob == null)
            ProceduralMonitorState.status.value = ProceduralMonitorStatus.Running(
                batch.style.name.lowercase(),
                batchNumber,
                "next batch ${if (pendingAiBatch != null) "ready" else "playing"}",
            )
            proceduralOverlay?.update(
                "${batch.style.name.lowercase().replaceFirstChar { it.uppercase() }} • batch $batchNumber",
                "Next batch ${if (pendingAiBatch != null) "ready" else "generating"} • ${openingTeasesRemaining()} opening Tease left",
            )
            proceduralBatchNumber++
        }
    }

    private fun queueProceduralBatch(batch: ProceduralBatch, replaceImmediately: Boolean) {
        val multiplier = AiIntensitySettings.multiplier.value
        val levels = batch.pattern.steps.map { step ->
            if (step.intensity >= 100) 255
            else (step.intensity * multiplier * 255f / 100f).toInt().coerceIn(0, 255)
        }.toIntArray()
        queueAiBatch(
            Intent()
                .putExtra(EXTRA_PATTERN_NAME, batch.pattern.name)
                .putExtra(EXTRA_PATTERN_LEVELS, levels)
                .putExtra(EXTRA_PATTERN_DURATIONS, batch.pattern.steps.map { it.durationMs }.toLongArray())
                .putExtra(EXTRA_REPLACE_IMMEDIATELY, replaceImmediately),
        )
    }

    private fun handleProceduralClose() {
        if (proceduralJob == null && !proceduralFinishing) return
        if (proceduralFinishing) return
        val styleName = currentAiBatch?.name.orEmpty()
        when {
            styleName.contains("Denial", ignoreCase = true) -> triggerDenialPause()
            styleName.contains("Edge", ignoreCase = true) -> triggerEdgeClose()
            Random.nextInt(100) < currentEdgeCloseChance() -> triggerEdgeClose()
            else -> triggerFinish()
        }
    }

    private fun triggerDenialPause() {
        proceduralJob?.cancel()
        proceduralJob = serviceScope.launch {
            val pause = Random.nextLong(15_000L, 25_001L)
            val steps = if (pause > 20_000L) listOf(
                PatternStep(0, (pause - 3_000L) / 2),
                PatternStep(100, 3_000L),
                PatternStep(0, (pause - 3_000L) - (pause - 3_000L) / 2),
            ) else listOf(PatternStep(0, pause))
            ProceduralMonitorState.status.value = ProceduralMonitorStatus.Special("Denial • paused")
            proceduralOverlay?.update("Denial • pause", "Close jumped directly to the pause")
            queueProceduralBatch(localProceduralBatch(ProceduralStyle.DENIAL, "Forced denial pause", steps), true)
            delay(pause)
            clearAiQueue()
            setLevel(0)
            runProceduralLoop(SecretStore(this@KnightControlService).loadApiKey())
        }
    }

    private fun triggerEdgeClose() {
        proceduralJob?.cancel()
        proceduralJob = serviceScope.launch {
            clearAiQueue()
            setLevel(0)
            val stopDuration = Random.nextLong(10_000L, 20_001L)
            val special = stopDuration < 15_000L && Random.nextBoolean()
            ProceduralMonitorState.status.value = ProceduralMonitorStatus.Special("Close • stopped")
            proceduralOverlay?.update("Close • stopped", "Waiting ${stopDuration / 1000}s")
            if (!special) {
                delay(stopDuration)
                runProceduralLoop(SecretStore(this@KnightControlService).loadApiKey(), chooseCloseBiasedStyle())
                return@launch
            }
            delay(5_000L)
            val doubleBatch = Random.nextInt(100) < 25
            val firstDuration = if (doubleBatch) 20_000L else Random.nextLong(10_000L, 20_001L)
            val client = GrokProceduralClient()
            val first = runCatching {
                withContext(Dispatchers.IO) {
                    client.generate(
                        SecretStore(this@KnightControlService).loadApiKey(),
                        setOf(ProceduralStyle.TEASE),
                        emptyList(),
                        proceduralBatchNumber,
                        openingTeasesRemaining(),
                        ProceduralStyle.TEASE,
                        "Create a low-only Tease with no high bursts, lasting about ${firstDuration}ms.",
                    )
                }
            }.getOrElse {
                localProceduralBatch(
                    ProceduralStyle.TEASE,
                    "Local low-only fallback",
                    List(10) { PatternStep(Random.nextInt(3, 23), firstDuration / 10) },
                )
            }.let { batch ->
                batch.copy(pattern = batch.pattern.copy(steps = fitSpecialTease(batch.pattern.steps, firstDuration, false)))
            }
            proceduralOverlay?.update("Special tease", if (doubleBatch) "Low-only batch 1/2" else "Low only")
            val specialPlaybackStartedAt = System.currentTimeMillis()
            queueProceduralBatch(first, true)
            var totalDuration = first.pattern.durationMs
            if (doubleBatch) {
                val second = runCatching {
                    withContext(Dispatchers.IO) {
                        client.generate(
                            SecretStore(this@KnightControlService).loadApiKey(),
                            setOf(ProceduralStyle.TEASE),
                            listOf(first.summary),
                            proceduralBatchNumber + 1,
                            openingTeasesRemaining(),
                            ProceduralStyle.TEASE,
                            "Create a full 20-second Tease with exactly 1-3 brief high bursts, each at most one second.",
                        )
                    }
                }.getOrElse {
                    localProceduralBatch(
                        ProceduralStyle.TEASE,
                        "Local double-Tease fallback",
                        List(10) { PatternStep(Random.nextInt(3, 23), 2_000L) },
                    )
                }.let { batch ->
                    batch.copy(pattern = batch.pattern.copy(steps = fitSpecialTease(batch.pattern.steps, 20_000L, true)))
                }
                queueProceduralBatch(second, false)
                totalDuration += second.pattern.durationMs
                proceduralOverlay?.update("Double tease", "Batch 2 ready • 1-3 brief bursts")
            }
            val elapsedPlayback = System.currentTimeMillis() - specialPlaybackStartedAt
            delay((totalDuration - elapsedPlayback).coerceAtLeast(0L))
            clearAiQueue()
            setLevel(0)
            proceduralOverlay?.update("Special tease • break", "Ten seconds off")
            delay(10_000L)
            runProceduralLoop(SecretStore(this@KnightControlService).loadApiKey())
        }
    }

    private fun triggerFinish() {
        proceduralJob?.cancel()
        proceduralJob = null
        proceduralFinishing = true
        clearAiQueue()
        ProceduralMonitorState.status.value = ProceduralMonitorStatus.Special("Finish • until Stop")
        proceduralOverlay?.update("Finish", "100% ↔ 50% every two seconds • until Stop")
        playPattern(
            Intent()
                .putExtra(EXTRA_PATTERN_NAME, "Finish")
                .putExtra(EXTRA_PATTERN_LEVELS, intArrayOf(255, 128))
                .putExtra(EXTRA_PATTERN_DURATIONS, longArrayOf(1_000L, 1_000L))
                .putExtra(EXTRA_PATTERN_REPEATS, 0)
                .putExtra(EXTRA_BLEND_FROM_CURRENT, true)
                .putExtra(EXTRA_SMOOTH_TRANSITIONS, true),
        )
    }

    private fun fitSpecialTease(source: List<PatternStep>, targetMs: Long, allowBursts: Boolean): List<PatternStep> {
        val normalized = source.map { step -> step.copy(intensity = step.intensity.coerceIn(1, 22)) }
        val total = normalized.sumOf { it.durationMs }.coerceAtLeast(1L)
        val fitted = normalized.map {
            it.copy(durationMs = (it.durationMs * targetMs / total).coerceAtLeast(100L))
        }
        if (!allowBursts || fitted.isEmpty()) return fitted
        val burstCount = Random.nextInt(1, minOf(3, fitted.size) + 1)
        val burstIndices = fitted.indices.shuffled().take(burstCount).toSet()
        return fitted.mapIndexed { index, step ->
            if (index in burstIndices) {
                step.copy(
                    intensity = Random.nextInt(55, 100),
                    durationMs = step.durationMs.coerceAtMost(1_000L),
                )
            } else step
        }
    }

    private fun localProceduralBatch(style: ProceduralStyle, summary: String, steps: List<PatternStep>) =
        ProceduralBatch(
            style,
            summary,
            com.towersys.adaptiveremote.patterns.KnightPattern(
                "procedural-local-${System.currentTimeMillis()}",
                "Procedural • ${style.name.lowercase().replaceFirstChar { it.uppercase() }}",
                steps,
            ),
        )

    private fun chooseCloseBiasedStyle(): ProceduralStyle {
        val teaseAllowed = consecutiveTeases < 2 &&
            (proceduralBatchNumber > 8 || openingTeasesUsed < 4)
        if (!teaseAllowed) return listOf(
            ProceduralStyle.STANDARD,
            ProceduralStyle.DENIAL,
            ProceduralStyle.EDGE,
        ).random()
        return when (Random.nextInt(100)) {
            in 0..69 -> ProceduralStyle.TEASE
            in 70..79 -> ProceduralStyle.STANDARD
            in 80..89 -> ProceduralStyle.DENIAL
            else -> ProceduralStyle.EDGE
        }
    }

    private fun currentEdgeCloseChance(): Int {
        val minutes = ((System.currentTimeMillis() - proceduralSessionStartedAt) / 60_000L).toInt()
        return (95 - (minutes - 4).coerceAtLeast(0) * 5).coerceAtLeast(0)
    }

    private fun openingTeasesRemaining(): Int =
        if (proceduralBatchNumber <= 8) (4 - openingTeasesUsed).coerceAtLeast(0) else 0

    private fun stopProcedural() {
        cancelProceduralUi()
        stopOutput()
    }

    private fun cancelProceduralUi() {
        proceduralJob?.cancel()
        proceduralJob = null
        proceduralOverlay?.remove()
        proceduralOverlay = null
        proceduralFinishing = false
        ProceduralMonitorState.status.value = ProceduralMonitorStatus.Idle
    }

    private fun stopOutput() {
        clearAiQueue()
        patternGeneration++
        patternJob?.cancel()
        patternJob = null
        DeviceControlState.patternPlayback.value = PatternPlaybackState.Idle
        setLevel(0)
    }

    @SuppressLint("MissingPermission")
    private fun shutdown() {
        cancelProceduralUi()
        stopTextMonitor()
        runCatching { stopOutput() }
        closeGatt()
        DeviceControlState.outputLevel.value = 0
        DeviceControlState.connection.value = DeviceConnectionStatus.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        currentProtocol = null
    }

    @SuppressLint("MissingPermission")
    private fun closeGattWithoutDisconnect() {
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
    }

    private fun scheduleReconnect(message: String) {
        if (userRequestedDisconnect) return
        val device = currentDevice ?: return
        DeviceControlState.outputLevel.value = 0
        DeviceControlState.connection.value = DeviceConnectionStatus.Connecting(device)
        updateNotification(message)
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(RECONNECT_DELAY_MS)
            reconnectJob = null
            connect(device)
        }
    }

    private fun fail(message: String) {
        DeviceControlState.outputLevel.value = 0
        DeviceControlState.connection.value = DeviceConnectionStatus.Error(message)
        updateNotification("Adaptive Remote needs attention")
    }

    private fun rejectConnection(message: String) {
        reconnectJob?.cancel()
        runCatching { stopOutput() }
        closeGatt()
        currentDevice = null
        fail(message)
    }

    override fun onDestroy() {
        proceduralOverlay?.remove()
        ProceduralMonitorState.status.value = ProceduralMonitorStatus.Idle
        textMonitorOverlay?.remove()
        TextMonitorState.status.value = TextMonitorStatus.Idle
        if (writeCharacteristic != null) runCatching { stopOutput() }
        serviceScope.cancel()
        closeGatt()
        DeviceControlState.outputLevel.value = 0
        DeviceControlState.connection.value = DeviceConnectionStatus.Disconnected
        super.onDestroy()
    }

    private fun hasBluetoothPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Device connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps manual control connected and provides an emergency stop."
            },
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Adaptive Remote")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
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
            "STOP",
            PendingIntent.getService(
                this,
                1,
                Intent(this, KnightControlService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_CONNECT = "com.towersys.adaptiveremote.action.CONNECT"
        const val ACTION_SET_LEVEL = "com.towersys.adaptiveremote.action.SET_LEVEL"
        const val ACTION_PLAY_PATTERN = "com.towersys.adaptiveremote.action.PLAY_PATTERN"
        const val ACTION_QUEUE_AI_BATCH = "com.towersys.adaptiveremote.action.QUEUE_AI_BATCH"
        const val ACTION_STOP = "com.towersys.adaptiveremote.action.STOP"
        const val ACTION_START_TEXT_MONITOR = "com.towersys.adaptiveremote.action.START_TEXT_MONITOR"
        const val ACTION_STOP_TEXT_MONITOR = "com.towersys.adaptiveremote.action.STOP_TEXT_MONITOR"
        const val ACTION_START_PROCEDURAL = "com.towersys.adaptiveremote.action.START_PROCEDURAL"
        const val ACTION_PROCEDURAL_CLOSE = "com.towersys.adaptiveremote.action.PROCEDURAL_CLOSE"
        const val ACTION_STOP_PROCEDURAL = "com.towersys.adaptiveremote.action.STOP_PROCEDURAL"
        const val ACTION_DISCONNECT = "com.towersys.adaptiveremote.action.DISCONNECT"
        const val EXTRA_NAME = "name"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_PROTOCOL_ID = "protocol_id"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_PATTERN_NAME = "pattern_name"
        const val EXTRA_PATTERN_LEVELS = "pattern_levels"
        const val EXTRA_PATTERN_DURATIONS = "pattern_durations"
        const val EXTRA_PATTERN_REPEATS = "pattern_repeats"
        const val EXTRA_BLEND_FROM_CURRENT = "blend_from_current"
        const val EXTRA_SMOOTH_TRANSITIONS = "smooth_transitions"
        const val EXTRA_HOLD_FINAL_LEVEL = "hold_final_level"
        const val EXTRA_REPLACE_IMMEDIATELY = "replace_immediately"
        private const val CHANNEL_ID = "knight_connection"
        private const val NOTIFICATION_ID = 4101
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val TEXT_STABILITY_DELAY_MS = 250L
        private const val TEXT_POLL_DELAY_MS = 200L
        private const val TEXT_RETRY_DELAY_MS = 5_000L
        private const val BLEND_STEPS = 8
        private const val BLEND_STEP_DELAY_MS = 50L
        private const val AI_STEP_BLEND_DURATION_MS = 400L
        private const val AI_STEP_BLEND_INTERVAL_MS = 50L
        private const val TEXT_SNIPPET_LENGTH = 82
        private const val MAX_AI_FALLBACK_LOOPS = 3
        private const val PROCEDURAL_QUEUE_POLL_MS = 250L
        private const val PROCEDURAL_RETRY_MS = 3_000L
    }
}

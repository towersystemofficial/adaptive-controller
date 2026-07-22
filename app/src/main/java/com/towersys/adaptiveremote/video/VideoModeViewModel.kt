package com.towersys.adaptiveremote.video

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.towersys.adaptiveremote.device.control.DeviceControlState
import com.towersys.adaptiveremote.device.control.VideoMonitorState
import com.towersys.adaptiveremote.text.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VideoModeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    val connection = DeviceControlState.connection
    val status = VideoMonitorState.status
    private val _hasApiKey = MutableStateFlow(SecretStore(app).loadApiKey().isNotBlank())
    val hasApiKey = _hasApiKey.asStateFlow()

    fun refreshKeyStatus() {
        _hasApiKey.value = SecretStore(app).loadApiKey().isNotBlank()
    }

    fun start(resultCode: Int, captureData: Intent) {
        ContextCompat.startForegroundService(
            app,
            Intent(app, VideoAnalysisService::class.java)
                .setAction(VideoAnalysisService.ACTION_START)
                .putExtra(VideoAnalysisService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(VideoAnalysisService.EXTRA_CAPTURE_DATA, captureData),
        )
    }

    fun stop() {
        app.startService(Intent(app, VideoAnalysisService::class.java).setAction(VideoAnalysisService.ACTION_STOP))
    }
}

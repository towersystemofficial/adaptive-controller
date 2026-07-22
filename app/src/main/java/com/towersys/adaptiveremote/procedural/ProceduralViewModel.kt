package com.towersys.adaptiveremote.procedural

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.towersys.adaptiveremote.device.control.KnightControlService
import com.towersys.adaptiveremote.device.control.DeviceControlState
import com.towersys.adaptiveremote.device.control.ProceduralMonitorState
import com.towersys.adaptiveremote.text.SecretStore
import com.towersys.adaptiveremote.video.VideoAnalysisService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProceduralViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    val connection = DeviceControlState.connection
    val status = ProceduralMonitorState.status
    private val _hasApiKey = MutableStateFlow(SecretStore(app).loadApiKey().isNotBlank())
    val hasApiKey = _hasApiKey.asStateFlow()

    fun refreshKeyStatus() { _hasApiKey.value = SecretStore(app).loadApiKey().isNotBlank() }

    fun start() {
        viewModelScope.launch {
            app.startService(Intent(app, VideoAnalysisService::class.java).setAction(VideoAnalysisService.ACTION_STOP))
            delay(300L)
            app.startService(Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_START_PROCEDURAL))
        }
    }

    fun stop() {
        app.startService(Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP_PROCEDURAL))
    }
}

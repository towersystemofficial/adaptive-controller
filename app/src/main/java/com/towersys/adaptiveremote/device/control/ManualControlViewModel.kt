package com.towersys.adaptiveremote.device.control

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ManualControlViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val store = KnownDeviceStore(app)
    val knownDevice = DeviceControlState.knownDevice
    val connection = DeviceControlState.connection
    val outputLevel = DeviceControlState.outputLevel
    private var previewJob: Job? = null

    init {
        DeviceControlState.knownDevice.value = store.load()
    }

    fun setLevel(level: Int) {
        previewJob?.cancel()
        app.startService(
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_SET_LEVEL)
                .putExtra(KnightControlService.EXTRA_LEVEL, level.coerceIn(0, 255)),
        )
    }

    fun previewLevel(level: Int) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(60)
            app.startService(
                Intent(app, KnightControlService::class.java)
                    .setAction(KnightControlService.ACTION_SET_LEVEL)
                    .putExtra(KnightControlService.EXTRA_LEVEL, level.coerceIn(0, 255)),
            )
        }
    }

    fun stop() {
        previewJob?.cancel()
        app.startService(
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_STOP),
        )
    }

}

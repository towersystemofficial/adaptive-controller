package com.towersys.adaptiveremote.device.control

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.towersys.adaptiveremote.device.protocol.DeviceCapability
import com.towersys.adaptiveremote.device.protocol.DeviceProtocolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManualControlViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val store = KnownDeviceStore(app)
    val knownDevice = DeviceControlState.knownDevice
    val connection = DeviceControlState.connection
    val outputLevel = DeviceControlState.outputLevel
    val capabilities = knownDevice
        .map { device ->
            device?.let { DeviceProtocolRegistry.findById(it.protocolId)?.capabilities }.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private var previewJob: Job? = null

    init {
        DeviceControlState.knownDevice.value = store.load()
    }

    fun setLevel(capability: DeviceCapability, level: Int) {
        previewJob?.cancel()
        app.startService(
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_SET_LEVEL)
                .putExtra(KnightControlService.EXTRA_CAPABILITY, capability.name)
                .putExtra(KnightControlService.EXTRA_LEVEL, level.coerceIn(0, 255)),
        )
    }

    fun previewLevel(capability: DeviceCapability, level: Int) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(60)
            app.startService(
                Intent(app, KnightControlService::class.java)
                    .setAction(KnightControlService.ACTION_SET_LEVEL)
                    .putExtra(KnightControlService.EXTRA_CAPABILITY, capability.name)
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

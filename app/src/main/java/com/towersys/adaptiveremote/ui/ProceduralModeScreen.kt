package com.towersys.adaptiveremote.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.device.control.KnightConnectionStatus
import com.towersys.adaptiveremote.device.control.ProceduralMonitorStatus
import com.towersys.adaptiveremote.procedural.ProceduralViewModel

@Composable
fun ProceduralModeScreen(viewModel: ProceduralViewModel = viewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val multiplier by AiIntensitySettings.multiplier.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { viewModel.refreshKeyStatus(); onDispose { } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Grok continuously composes bounded batches and chooses between Tease, Standard, Denial, and Edge. " +
                    "The floating Close control resolves the current procedural state; Stop always ends the session.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Procedural status", style = MaterialTheme.typography.titleMedium)
                    Text(status.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Home AI multiplier: %.2f×".format(multiplier))
                    if (!hasApiKey) Text("Save your xAI key in Text mode first.")
                    if (connection !is KnightConnectionStatus.Ready) Text("Scan and connect a compatible device first.")
                }
            }
        }
        item {
            if (status == ProceduralMonitorStatus.Idle || status is ProceduralMonitorStatus.Error) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasApiKey && connection is KnightConnectionStatus.Ready,
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else viewModel.start()
                    },
                ) { Text(if (Settings.canDrawOverlays(context)) "Start procedural session" else "Allow floating control") }
            } else {
                FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = viewModel::stop) { Text("STOP") }
            }
        }
    }
}

private fun ProceduralMonitorStatus.summary(): String = when (this) {
    ProceduralMonitorStatus.Idle -> "Off"
    ProceduralMonitorStatus.Starting -> "Grok is composing the first batch"
    is ProceduralMonitorStatus.Running -> "$style • batch $batch • $detail"
    is ProceduralMonitorStatus.Special -> detail
    is ProceduralMonitorStatus.Error -> message
}

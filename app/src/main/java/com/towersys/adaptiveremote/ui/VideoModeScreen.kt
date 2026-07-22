package com.towersys.adaptiveremote.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.device.control.KnightConnectionStatus
import com.towersys.adaptiveremote.device.control.VideoMonitorStatus
import com.towersys.adaptiveremote.video.VideoModeViewModel

@Composable
fun VideoModeScreen(viewModel: VideoModeViewModel = viewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val multiplier by AiIntensitySettings.multiplier.collectAsStateWithLifecycle()
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.start(result.resultCode, data)
        }
    }

    DisposableEffect(Unit) {
        viewModel.refreshKeyStatus()
        onDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Video mode captures the screen only while active. Every cluster contains exactly five " +
                    "frames spaced evenly across one second and is labeled that way for Grok.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Live status", style = MaterialTheme.typography.titleMedium)
                    Text(status.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            !hasApiKey -> "Save your xAI key in Text mode first."
                            connection !is KnightConnectionStatus.Ready -> "The device is reconnecting."
                            else -> "The device and Grok are ready."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home AI multiplier: %.2f×".format(multiplier))
                    Text(
                        "Visible explicit content opens the gate; local frame motion controls amplification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            if (status == VideoMonitorStatus.Idle || status is VideoMonitorStatus.Error) {
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
                        } else {
                            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    },
                ) {
                    Text(if (Settings.canDrawOverlays(context)) "Start screen-video analysis" else "Allow floating control")
                }
            } else {
                FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = viewModel::stop) {
                    Text("STOP VIDEO")
                }
            }
        }
        item {
            Text(
                "Android will show its screen-sharing confirmation before each session. Protected video may " +
                    "appear blank and will therefore produce zero output.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun VideoMonitorStatus.summary(): String = when (this) {
    VideoMonitorStatus.Idle -> "Off"
    VideoMonitorStatus.Capturing -> "Capturing five frames over one second"
    VideoMonitorStatus.Analyzing -> "Grok is analyzing the newest complete cluster"
    is VideoMonitorStatus.Playing -> "Responding • $summary"
    is VideoMonitorStatus.Error -> message
}

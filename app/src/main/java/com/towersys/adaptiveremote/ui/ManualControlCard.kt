package com.towersys.adaptiveremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.device.control.KnightConnectionStatus
import com.towersys.adaptiveremote.device.control.ManualControlViewModel
import kotlin.math.roundToInt

@Composable
fun ManualControlCard(viewModel: ManualControlViewModel = viewModel()) {
    val knownDevice by viewModel.knownDevice.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val outputLevel by viewModel.outputLevel.collectAsStateWithLifecycle()
    val isReady = connection is KnightConnectionStatus.Ready
    var selectedLevel by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(outputLevel) {
        selectedLevel = outputLevel / 255f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manual control", style = MaterialTheme.typography.titleMedium)
                    Text(
                        connection.summary(knownDevice?.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connection is KnightConnectionStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (!isReady) {
                Text(
                    "Use Device setup on the home screen to scan and start the persistent connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Output", style = MaterialTheme.typography.titleMedium)
                Text("${(selectedLevel * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = selectedLevel,
                onValueChange = {
                    selectedLevel = it
                    viewModel.previewLevel((it * 255).roundToInt())
                },
                onValueChangeFinished = {
                    viewModel.setLevel((selectedLevel * 255).roundToInt())
                },
                enabled = isReady,
                valueRange = 0f..1f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(0, 25, 50, 75, 100).forEach { percent ->
                    OutlinedButton(
                        enabled = isReady,
                        onClick = {
                            selectedLevel = percent / 100f
                            viewModel.setLevel((percent * 255f / 100f).roundToInt())
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 8.dp,
                        ),
                    ) { Text("$percent") }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = isReady,
                onClick = {
                    selectedLevel = 0f
                    viewModel.stop()
                },
            ) {
                Text("STOP NOW")
            }
            Text(
                "The notification also provides Stop while Adaptive Remote is in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun KnightConnectionStatus.summary(knownName: String?): String = when (this) {
    KnightConnectionStatus.Disconnected -> if (knownName == null) "No saved device" else "$knownName disconnected"
    is KnightConnectionStatus.Connecting -> "Connecting to ${device.name}…"
    is KnightConnectionStatus.Ready -> "${device.name} connected"
    is KnightConnectionStatus.Error -> message
}

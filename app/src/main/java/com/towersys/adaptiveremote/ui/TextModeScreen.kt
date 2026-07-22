package com.towersys.adaptiveremote.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.device.control.DeviceConnectionStatus
import com.towersys.adaptiveremote.device.control.TextMonitorStatus
import com.towersys.adaptiveremote.text.TextAnalysisState
import com.towersys.adaptiveremote.text.TextModeViewModel

@Composable
fun TextModeScreen(viewModel: TextModeViewModel = viewModel()) {
    val context = LocalContext.current
    val captured by viewModel.capturedText.collectAsStateWithLifecycle()
    val sourceApp by viewModel.sourceApp.collectAsStateWithLifecycle()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val monitorStatus by viewModel.monitorStatus.collectAsStateWithLifecycle()
    var passage by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val multiplier by AiIntensitySettings.multiplier.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Capture visible text from another app, review it here, then explicitly send it to Grok for a timeline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Visible-text access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (accessibilityConnected) "Enabled${sourceApp.takeIf { it.isNotBlank() }?.let { " • last capture: $it" }.orEmpty()}"
                        else "Enable Adaptive Remote in Android Accessibility settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text(if (accessibilityConnected) "Accessibility settings" else "Enable access") }
                    Button(
                        enabled = captured.isNotBlank(),
                        onClick = { passage = captured },
                    ) { Text("Refresh visible text") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Grok connection", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (hasApiKey) "API key saved — enter to replace" else "xAI API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Button(enabled = apiKey.isNotBlank(), onClick = {
                        viewModel.saveApiKey(apiKey)
                        apiKey = ""
                    }) { Text("Save key securely") }
                    Text(
                        "The key is encrypted with Android Keystore. Text is sent only when Analyze is pressed " +
                            "or while Continuous reading is explicitly active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = passage,
                onValueChange = { passage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Passage to interpret") },
                minLines = 6,
                maxLines = 14,
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = hasApiKey && passage.isNotBlank() && analysis !is TextAnalysisState.Analyzing,
                onClick = { viewModel.analyze(passage) },
            ) {
                if (analysis is TextAnalysisState.Analyzing) CircularProgressIndicator(Modifier.padding(2.dp))
                else Text("Analyze whole passage")
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Continuous reading", style = MaterialTheme.typography.titleMedium)
                    Text(monitorStatus.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Visible text is refreshed continuously. Grok analyzes both the whole scene and its " +
                            "individual parts, prefetches one bounded batch, and preserves ongoing action across " +
                            "interspersed dialogue. The previous batch loops if Grok is late.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (monitorStatus == TextMonitorStatus.Idle || monitorStatus is TextMonitorStatus.Error) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasApiKey && accessibilityConnected && connection is DeviceConnectionStatus.Ready,
                            onClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                } else {
                                    viewModel.startContinuous()
                                }
                            },
                        ) { Text(if (Settings.canDrawOverlays(context)) "Start continuous mode" else "Allow floating control") }
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = viewModel::stopContinuous,
                        ) { Text("Stop continuous mode") }
                    }
                }
            }
        }
        when (val result = analysis) {
            is TextAnalysisState.Ready -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(result.interpretation.pattern.name, style = MaterialTheme.typography.titleLarge)
                        Text(result.interpretation.summary)
                        result.interpretation.pattern.steps.forEachIndexed { index, step ->
                            Text("${index + 1}. ${step.intensity}% for ${step.durationMs / 1000f}s")
                        }
                        Text("Home AI multiplier: %.2f×".format(multiplier))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(modifier = Modifier.weight(1f), onClick = viewModel::stop) {
                                Text("STOP")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = connection is DeviceConnectionStatus.Ready,
                                onClick = { viewModel.play(result.interpretation.pattern) },
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text(" Play")
                            }
                        }
                        if (connection !is DeviceConnectionStatus.Ready) {
                            Text("Scan and connect a compatible device from the home screen before playing.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            is TextAnalysisState.Error -> item { Text(result.message, color = MaterialTheme.colorScheme.error) }
            else -> Unit
        }
    }
}

private fun TextMonitorStatus.summary(): String = when (this) {
    TextMonitorStatus.Idle -> "Off"
    TextMonitorStatus.WaitingForText -> "Active • waiting for a new visible passage"
    is TextMonitorStatus.Analyzing -> "Analyzing text from $sourceApp"
    is TextMonitorStatus.Playing -> "Playing • $summary"
    is TextMonitorStatus.Error -> message
}

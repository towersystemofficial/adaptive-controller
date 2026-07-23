package com.towersys.adaptiveremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.device.control.DeviceConnectionStatus
import com.towersys.adaptiveremote.device.control.PatternPlaybackState
import com.towersys.adaptiveremote.patterns.BuiltInPatterns
import com.towersys.adaptiveremote.patterns.KnightPattern
import com.towersys.adaptiveremote.patterns.PatternStep
import com.towersys.adaptiveremote.patterns.PatternViewModel
import com.towersys.adaptiveremote.patterns.generateProceduralPattern
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PatternScreen(viewModel: PatternViewModel = viewModel()) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<KnightPattern?>(null) }
    var repeats by remember { mutableIntStateOf(1) }
    val allPatterns = (saved.filter { it.isFavorite } + BuiltInPatterns.all + saved)
        .distinctBy { it.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Build, save, and run intensity timelines. Stop is always available in the app and notification.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            PlaybackCard(
                playback = playback,
                connected = connection is DeviceConnectionStatus.Ready,
                onStop = viewModel::stop,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selected = KnightPattern(
                            id = "custom-${System.currentTimeMillis()}",
                            name = "My pattern",
                            steps = listOf(PatternStep(25, 750), PatternStep(60, 750)),
                        )
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(" New")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { selected = generateProceduralPattern() },
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Text(" Generate")
                }
            }
        }
        if (selected != null) {
            item {
                PatternEditor(
                    initial = selected!!,
                    onSave = {
                        viewModel.save(it)
                        selected = null
                    },
                    onCancel = { selected = null },
                )
            }
        }
        item { Text("Patterns", style = MaterialTheme.typography.titleLarge) }
        items(allPatterns, key = { it.id }) { pattern ->
            PatternCard(
                pattern = pattern,
                recentlyPlayed = pattern.id in history.take(3),
                repeats = repeats,
                connected = connection is DeviceConnectionStatus.Ready,
                onRepeatsChange = { repeats = it },
                onPlay = { viewModel.play(pattern, repeats) },
                onEdit = { selected = pattern.copy(id = if (pattern.isBuiltIn) "custom-${System.currentTimeMillis()}" else pattern.id) },
                onFavorite = { viewModel.toggleFavorite(pattern) },
                onDelete = if (pattern.isBuiltIn) null else {
                    { viewModel.delete(pattern.id) }
                },
            )
        }
    }
}

@Composable
private fun PlaybackCard(
    playback: PatternPlaybackState,
    connected: Boolean,
    onStop: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val playing = playback as? PatternPlaybackState.Playing
                Text(playing?.name ?: if (connected) "Ready" else "Device disconnected", style = MaterialTheme.typography.titleMedium)
                Text(
                    playing?.let { "Step ${it.step} of ${it.totalSteps}" }
                        ?: if (connected) "Choose a pattern below" else "Scan and connect from the home screen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(enabled = playback is PatternPlaybackState.Playing, onClick = onStop) {
                Text("STOP")
            }
        }
    }
}

@Composable
private fun PatternCard(
    pattern: KnightPattern,
    recentlyPlayed: Boolean,
    repeats: Int,
    connected: Boolean,
    onRepeatsChange: (Int) -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pattern.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pattern.steps.size} steps • ${formatDuration(pattern.durationMs)}" + if (recentlyPlayed) " • Recent" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (pattern.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                    )
                }
                if (onDelete != null) IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pattern.steps.forEach { step ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${step.intensity}%", style = MaterialTheme.typography.labelSmall)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + step.intensity / 130f),
                            ),
                        ) { androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Repeat")
                listOf(1 to "1", 3 to "3", 5 to "5", 0 to "∞").forEach { (count, label) ->
                    TextButton(
                        modifier = Modifier.widthIn(min = 38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        onClick = { onRepeatsChange(count) },
                    ) {
                        Text(if (repeats == count) "[$label]" else label)
                    }
                }
                if (repeats == 0) Text("until Stop", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onEdit) { Text("Edit") }
                Button(modifier = Modifier.weight(1f), enabled = connected, onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(" Play")
                }
            }
        }
    }
}

@Composable
private fun PatternEditor(
    initial: KnightPattern,
    onSave: (KnightPattern) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    val steps = remember(initial.id) { mutableStateListOf<PatternStep>().apply { addAll(initial.steps) } }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pattern editor", style = MaterialTheme.typography.titleLarge)
            TextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            steps.forEachIndexed { index, step ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Step ${index + 1}", modifier = Modifier.weight(1f))
                        Text("${step.intensity}% • ${step.durationMs / 1000f}s")
                        IconButton(onClick = { if (steps.size > 1) steps.removeAt(index) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove step")
                        }
                    }
                    Slider(
                        value = step.intensity.toFloat(),
                        onValueChange = { steps[index] = step.copy(intensity = it.roundToInt()) },
                        valueRange = 0f..100f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500L, 1_000L, 2_000L).forEach { duration ->
                            TextButton(onClick = { steps[index] = step.copy(durationMs = duration) }) {
                                Text(if (step.durationMs == duration) "[${duration / 1000f}s]" else "${duration / 1000f}s")
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { steps.add(PatternStep(50, 1_000)) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(" Add step")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(enabled = name.isNotBlank() && steps.isNotEmpty(), onClick = {
                    onSave(initial.copy(name = name.trim(), steps = steps.toList(), isBuiltIn = false))
                }) { Text("Save") }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String =
    String.format(Locale.US, "%.1fs", milliseconds / 1000f)

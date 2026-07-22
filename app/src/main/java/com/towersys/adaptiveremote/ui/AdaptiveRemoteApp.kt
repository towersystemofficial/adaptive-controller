package com.towersys.adaptiveremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.core.ControlMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveRemoteApp() {
    val context = LocalContext.current
    AiIntensitySettings.initialize(context)
    val multiplier by AiIntensitySettings.multiplier.collectAsStateWithLifecycle()
    var activeMode by remember { androidx.compose.runtime.mutableStateOf<ControlMode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeMode?.displayName ?: "Adaptive Remote")
                        Text(
                            text = "Private controller",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (activeMode != null) {
                        IconButton(onClick = { activeMode = null }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (activeMode) {
                null -> HomeScreen(multiplier, { AiIntensitySettings.set(context, it) }, { activeMode = it })
                ControlMode.MANUAL -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { item { ManualControlCard() } }
                ControlMode.PATTERNS -> PatternScreen()
                ControlMode.TEXT -> TextModeScreen()
                ControlMode.VIDEO -> VideoModeScreen()
                ControlMode.AUTONOMOUS -> ProceduralModeScreen()
                else -> PlannedModeScreen(activeMode!!)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    multiplier: Float,
    onMultiplierChange: (Float) -> Unit,
    onModeSelected: (ControlMode) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DeviceDiagnosticCard() }
        item { MultiplierCard(multiplier, onMultiplierChange) }
        item { Text("Modes", style = MaterialTheme.typography.titleLarge) }
        items(modeCards) { mode -> ModeCard(mode) { onModeSelected(mode.mode) } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PlannedModeScreen(mode: ControlMode) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${mode.displayName} mode", style = MaterialTheme.typography.titleLarge)
        Text("This mode is planned for a later checkpoint.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MultiplierCard(
    multiplier: Float,
    onMultiplierChange: (Float) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Adaptive multiplier", style = MaterialTheme.typography.titleMedium)
                Text("%.2f×".format(multiplier), style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = multiplier,
                onValueChange = onMultiplierChange,
                valueRange = 0f..1.5f,
                steps = 29,
            )
            Text(
                "Persistent AI scale for Text, Video, and ordinary Procedural output. Manual and saved " +
                    "Patterns remain direct; explicit Procedural maximum steps still use full device output.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeCard(mode: ModeCardModel, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(mode.icon, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.mode.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private data class ModeCardModel(
    val mode: ControlMode,
    val description: String,
    val icon: ImageVector,
)

private val modeCards = listOf(
    ModeCardModel(ControlMode.MANUAL, "Direct controls for every confirmed function.", Icons.Rounded.ShowChart),
    ModeCardModel(ControlMode.PATTERNS, "Saved patterns and timeline editing.", Icons.Rounded.ShowChart),
    ModeCardModel(ControlMode.TEXT, "Interpret visible passages and described events.", Icons.Rounded.Description),
    ModeCardModel(ControlMode.VIDEO, "Local motion plus five-frame Grok analysis.", Icons.Rounded.Movie),
    ModeCardModel(ControlMode.AUTONOMOUS, "Procedurally generated sessions.", Icons.Rounded.AutoAwesome),
)

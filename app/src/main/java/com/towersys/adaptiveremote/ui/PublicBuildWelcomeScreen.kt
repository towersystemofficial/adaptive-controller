package com.towersys.adaptiveremote.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PublicBuildWelcomeScreen(onAccept: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome to Adaptive Remote", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "This is an early public development preview for compatible JoyHub BLE devices.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            NoticeCard(
                title = "Stay in control",
                body = "Start at zero, test while you can reach the device, and use Stop immediately if " +
                    "anything feels wrong. Software and wireless links can fail; do not rely on the app as " +
                    "the only way to stop or remove hardware.",
            )
        }
        item {
            NoticeCard(
                title = "Understand AI sharing",
                body = "Manual controls and saved patterns stay on this device. Text, Video, and " +
                    "AI Procedural modes require your own xAI API key. When you start those modes, the " +
                    "relevant text, sampled screen frames, or generation request is sent directly to xAI.",
            )
        }
        item {
            NoticeCard(
                title = "Preview limitations",
                body = "Only the J-Mars/JoyHub FFA0/FFA1 transport has been validated. Compatibility, " +
                    "output behavior, and AI interpretations are not guaranteed. You are responsible for " +
                    "API charges and for reviewing permissions before enabling capture features.",
            )
        }
        item {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I understand — continue")
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

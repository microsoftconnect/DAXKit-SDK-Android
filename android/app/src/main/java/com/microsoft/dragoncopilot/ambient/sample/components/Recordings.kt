package com.microsoft.dragoncopilot.ambient.sample.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microsoft.dragoncopilot.ambient.sample.viewmodel.AmbientViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Recordings screen for a single session.
 *
 * Shows completed recordings and provides a floating action button to start/stop recording via the Ambient SDK.
 * While recording, a live waveform visualization driven by [AmbientRecording.Listener.onRecordedAudio]
 * audio level callbacks is displayed above the recording list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    sessionId: String,
    viewModel: AmbientViewModel = viewModel(factory = AmbientViewModel.Factory),
    modifier: Modifier,
    onBack: () -> Unit,
) {
    val sessions = viewModel.sessions.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val session = sessions.value.find { it.sessionId == sessionId }
    val recordings = session?.recordings ?: emptyList()
    val isRecording = recordingState.isRecording && recordingState.currentSessionId == sessionId
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val id = session?.sessionId ?: "Recordings"
                    Text(if (id.length > 12) "${id.take(8)}…${id.takeLast(4)}" else id)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.startRecording(sessionId)
                    }
                },
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live recording indicator
            if (isRecording) {
                RecordingIndicator(
                    elapsedMs = recordingState.elapsedMs,
                    audioLevel = recordingState.audioLevel,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(recordings) { recording ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recording.recordingId,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = dateFormat.format(Date(recording.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = formatDuration(recording.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Visual indicator shown during an active recording with a waveform driven by SDK audio levels. */
@Composable
private fun RecordingIndicator(
    elapsedMs: Long,
    audioLevel: Float,
) {
    // Amplify low audio levels: apply log scale so quiet audio is still visible
    val amplified = if (audioLevel > 0f) {
        (1f + kotlin.math.ln(audioLevel.coerceIn(0.001f, 1f)) / kotlin.math.ln(0.001f).let { -it }).let {
            // Map log range to 0..1, then boost
            ((kotlin.math.ln(audioLevel.coerceIn(0.001f, 1f)) + 7f) / 7f).coerceIn(0.15f, 1f)
        }
    } else 0.15f  // minimum visible level while recording

    val animatedLevel by animateFloatAsState(
        targetValue = amplified,
        animationSpec = tween(durationMillis = 100),
        label = "audioLevel",
    )
    val barColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Recording — ${formatDuration(elapsedMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Waveform bars
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val barCount = 40
                val barWidth = size.width / (barCount * 2f)
                val maxBarHeight = size.height
                val centerY = size.height / 2f

                for (i in 0 until barCount) {
                    // Create a wave-like pattern based on audio level
                    val distFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                    val scale = (1f - distFromCenter * 0.5f) * animatedLevel
                    val barHeight = max(barWidth, maxBarHeight * scale)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(
                            x = i * barWidth * 2f,
                            y = centerY - barHeight / 2f,
                        ),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

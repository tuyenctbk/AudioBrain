package com.example.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.ui.journal.formatDuration
import java.util.Locale

data class TranscriptSegment(
    val timestampSeconds: Long?,
    val timestampLabel: String?,
    val text: String
)

private val TIMESTAMP_LINE_REGEX = Regex("""^\s*[\[(]?(\d{1,2}):(\d{2})[\])]?\s*(.*)$""")

fun parseTranscriptSegments(transcript: String): List<TranscriptSegment> {
    if (transcript.isBlank()) return emptyList()
    return transcript.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            val match = TIMESTAMP_LINE_REGEX.find(line)
            if (match != null) {
                val mins = match.groupValues[1].toLongOrNull() ?: 0L
                val secs = match.groupValues[2].toLongOrNull()?.coerceAtMost(59L) ?: 0L
                val totalSeconds = mins * 60L + secs
                val label = String.format(Locale.US, "%02d:%02d", mins, secs)
                val body = match.groupValues[3].trim()
                TranscriptSegment(
                    timestampSeconds = totalSeconds,
                    timestampLabel = label,
                    text = body.ifBlank { line }
                )
            } else {
                TranscriptSegment(
                    timestampSeconds = null,
                    timestampLabel = null,
                    text = line
                )
            }
        }
}

fun replaceTranscriptSegment(
    transcript: String,
    segmentIndex: Int,
    newBody: String
): String {
    val segments = parseTranscriptSegments(transcript)
    if (segmentIndex !in segments.indices) return transcript
    val cleanedBody = newBody.replace("\r\n", " ").replace("\n", " ").trim()
    return segments.mapIndexedNotNull { idx, seg ->
        val body = if (idx == segmentIndex) cleanedBody else seg.text.trim()
        when {
            idx == segmentIndex && body.isBlank() && seg.timestampLabel == null -> null
            seg.timestampLabel != null -> {
                if (body.isBlank()) "[${seg.timestampLabel}]" else "[${seg.timestampLabel}] $body"
            }
            else -> body
        }
    }.joinToString("\n")
}

fun countTranscriptWords(transcript: String): Int {
    if (transcript.isBlank()) return 0
    return transcript
        .lines()
        .map { line -> TIMESTAMP_LINE_REGEX.find(line)?.groupValues?.get(3) ?: line }
        .joinToString(" ")
        .trim()
        .split(Regex("""\s+"""))
        .count { it.isNotBlank() }
}

@Composable
fun StudioAudioPlayerCard(
    playbackState: AudioPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!playbackState.hasAudio) return

    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val currentMs = playbackState.currentPositionMs.coerceIn(0L, durationMs)
    val sliderValue = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = playbackState.isPrepared, onClick = onTogglePlayPause)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playbackState.isPlaying) R.string.cd_pause_audio else R.string.cd_play_audio
                            ),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (playbackState.isPlaying) R.string.audio_playing_recording
                                    else R.string.audio_local_recording
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${formatDuration(currentMs / 1000L)} / ${formatDuration(playbackState.durationMs / 1000L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = sliderValue,
                        enabled = playbackState.isPrepared,
                        onValueChange = { fraction ->
                            onSeekTo((fraction * durationMs).toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCycleSpeed)
                ) {
                    Text(
                        text = "${playbackState.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            if (playbackState.errorMessage != null) {
                Text(
                    text = playbackState.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TimestampedTranscriptCard(
    transcript: String,
    playbackState: AudioPlaybackState,
    onSeekToSeconds: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(transcript) { parseTranscriptSegments(transcript) }
    val currentSec = playbackState.currentPositionMs / 1000L

    val activeSegmentIndex = remember(segments, currentSec, playbackState.hasAudio) {
        if (!playbackState.hasAudio) -1
        else {
            segments.indexOfLast { seg ->
                seg.timestampSeconds != null && seg.timestampSeconds <= currentSec
            }
        }
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        if (segments.isEmpty()) {
            Text(
                text = stringResource(R.string.transcript_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                segments.forEachIndexed { index, segment ->
                    val isActive = index == activeSegmentIndex
                    val highlightColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "transcript_segment_bg"
                    )

                    Surface(
                        color = highlightColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (isActive) 10.dp else 2.dp,
                                vertical = if (isActive) 8.dp else 2.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (segment.timestampLabel != null && segment.timestampSeconds != null) {
                                Surface(
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = playbackState.hasAudio && playbackState.isPrepared) {
                                            onSeekToSeconds(segment.timestampSeconds)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        if (playbackState.hasAudio) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = stringResource(
                                                    R.string.cd_jump_to_time,
                                                    segment.timestampLabel
                                                ),
                                                tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = segment.timestampLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = segment.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

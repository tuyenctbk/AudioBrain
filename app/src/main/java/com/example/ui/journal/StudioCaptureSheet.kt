package com.example.ui.journal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.RecordingState

@Composable
fun StudioCaptureSheet(
    captureState: CaptureBannerState,
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onTogglePauseRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancelRecording: () -> Unit,
    onOpenTypeMemo: () -> Unit,
    onImportAudio: () -> Unit,
    onToggleAutoFactCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    val pulseTransition = rememberInfiniteTransition(label = "studio_sheet_pulse")
    val idleRingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_ring_scale"
    )
    val idleOuterRingAlpha by pulseTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_outer_alpha"
    )
    val recRingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_ring_scale"
    )
    val recPulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_pulse_alpha"
    )

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetDragHandle(
                    isExpanded = isExpanded,
                    onToggle = { isExpanded = !isExpanded },
                    onDragDelta = { delta ->
                        dragAccumulated += delta
                        if (dragAccumulated > 48f) {
                            isExpanded = false
                            dragAccumulated = 0f
                        } else if (dragAccumulated < -48f) {
                            isExpanded = true
                            dragAccumulated = 0f
                        }
                    },
                    onDragReset = { dragAccumulated = 0f }
                )

                AnimatedContent(
                    targetState = captureState.phase,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "capture_sheet_phase"
                ) { phase ->
                    when (phase) {
                        CapturePhase.Idle -> {
                            if (isExpanded) {
                                ExpandedIdleContent(
                                    autoFactCheck = captureState.autoFactCheck,
                                    idleRingScale = idleRingScale,
                                    idleOuterRingAlpha = idleOuterRingAlpha,
                                    onToggleAutoFactCheck = onToggleAutoFactCheck,
                                    onImportAudio = onImportAudio,
                                    onStartRecording = onStartRecording,
                                    onOpenTypeMemo = onOpenTypeMemo
                                )
                            } else {
                                CompactIdleContent(
                                    idleRingScale = idleRingScale,
                                    idleOuterRingAlpha = idleOuterRingAlpha,
                                    onImportAudio = onImportAudio,
                                    onStartRecording = onStartRecording,
                                    onOpenTypeMemo = onOpenTypeMemo
                                )
                            }
                        }

                        CapturePhase.Recording -> {
                            if (isExpanded) {
                                ExpandedRecordingContent(
                                    recordingState = recordingState,
                                    recPulseAlpha = recPulseAlpha,
                                    recRingScale = recRingScale,
                                    onCancelRecording = onCancelRecording,
                                    onTogglePauseRecording = onTogglePauseRecording,
                                    onSaveDraft = onSaveDraft,
                                    onStopRecording = onStopRecording
                                )
                            } else {
                                CompactRecordingContent(
                                    recordingState = recordingState,
                                    recPulseAlpha = recPulseAlpha,
                                    recRingScale = recRingScale,
                                    onCancelRecording = onCancelRecording,
                                    onTogglePauseRecording = onTogglePauseRecording,
                                    onSaveDraft = onSaveDraft,
                                    onStopRecording = onStopRecording
                                )
                            }
                        }

                        CapturePhase.Transcribing, CapturePhase.FactChecking -> {
                            ProcessingPipelineContent(isSearching = phase == CapturePhase.FactChecking)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrossScreenRecordingSheet(
    recordingState: RecordingState,
    onTogglePauseRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSaveDraft: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "cross_screen_rec_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cross_screen_pulse_alpha"
    )
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cross_screen_ring_scale"
    )

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactRecordingContent(
                    recordingState = recordingState,
                    recPulseAlpha = pulseAlpha,
                    recRingScale = ringScale,
                    onCancelRecording = onCancelRecording,
                    onTogglePauseRecording = onTogglePauseRecording,
                    onSaveDraft = onSaveDraft,
                    onStopRecording = onStopRecording
                )
            }
        }
    }
}

@Composable
private fun SheetDragHandle(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragReset: () -> Unit
) {
    val handleCd = stringResource(
        if (isExpanded) R.string.cd_collapse_recording_sheet else R.string.cd_expand_recording_sheet
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragReset() },
                    onDragCancel = { onDragReset() },
                    onVerticalDrag = { _, dragAmount ->
                        onDragDelta(dragAmount)
                    }
                )
            }
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = handleCd
            }
            .testTag("capture_sheet_handle")
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ExpandedIdleContent(
    autoFactCheck: Boolean,
    idleRingScale: Float,
    idleOuterRingAlpha: Float,
    onToggleAutoFactCheck: (Boolean) -> Unit,
    onImportAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenTypeMemo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.auto_fact_check_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoFactCheck,
                onCheckedChange = onToggleAutoFactCheck,
                modifier = Modifier.testTag("auto_fact_check_switch")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onImportAudio,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("import_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_import_short),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            CentralVoiceToggleFab(
                isRecording = false,
                isPaused = false,
                compact = false,
                pulseScale = idleRingScale,
                pulseAlpha = idleOuterRingAlpha,
                latestAmplitude = 0.08f,
                onClick = onStartRecording
            )

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedButton(
                onClick = onOpenTypeMemo,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("type_memo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_type_short),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactIdleContent(
    idleRingScale: Float,
    idleOuterRingAlpha: Float,
    onImportAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenTypeMemo: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onImportAudio,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("import_audio_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.AudioFile,
                contentDescription = stringResource(R.string.action_import_audio),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CentralVoiceToggleFab(
            isRecording = false,
            isPaused = false,
            compact = true,
            pulseScale = idleRingScale,
            pulseAlpha = idleOuterRingAlpha,
            latestAmplitude = 0.08f,
            onClick = onStartRecording
        )

        IconButton(
            onClick = onOpenTypeMemo,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("type_memo_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = stringResource(R.string.action_type_memo),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Central Floating Action Button (FAB) that toggles voice recording in-place,
 * featuring a multi-ring pulsing sonar halo and live microphone amplitude reaction
 * to visually indicate that the app is actively listening.
 */
@Composable
private fun CentralVoiceToggleFab(
    isRecording: Boolean,
    isPaused: Boolean,
    compact: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    latestAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeListening = isRecording && !isPaused
    val amplitudeBoost by animateFloatAsState(
        targetValue = if (activeListening) {
            1f + (latestAmplitude.coerceIn(0.08f, 1f) * 0.22f)
        } else {
            1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_amp_boost"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !isRecording -> MaterialTheme.colorScheme.primary
            isPaused -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(220),
        label = "fab_container_color"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !isRecording -> MaterialTheme.colorScheme.onPrimary
            isPaused -> MaterialTheme.colorScheme.onTertiary
            else -> MaterialTheme.colorScheme.onError
        },
        animationSpec = tween(220),
        label = "fab_content_color"
    )

    val fabSize = if (compact) 52.dp else 68.dp
    val outerHaloSize = if (compact) 68.dp else 92.dp
    val innerHaloSize = if (compact) 60.dp else 80.dp
    val actionLabel = if (isRecording) {
        stringResource(R.string.action_stop_analyze)
    } else {
        stringResource(R.string.action_record)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(outerHaloSize)
        ) {
            // Outer sonar pulse ring
            if (!isPaused) {
                Box(
                    modifier = Modifier
                        .size(outerHaloSize)
                        .scale(pulseScale * amplitudeBoost)
                        .clip(CircleShape)
                        .background(
                            containerColor.copy(
                                alpha = if (activeListening) (pulseAlpha * 0.28f).coerceIn(0.08f, 0.32f)
                                else pulseAlpha
                            )
                        )
                )
                // Inner acoustic resonance ring
                Box(
                    modifier = Modifier
                        .size(innerHaloSize)
                        .scale(((pulseScale + 1f) / 2f) * amplitudeBoost)
                        .clip(CircleShape)
                        .background(
                            containerColor.copy(
                                alpha = if (activeListening) (pulseAlpha * 0.42f).coerceIn(0.14f, 0.46f)
                                else (pulseAlpha * 1.3f).coerceAtMost(0.32f)
                            )
                        )
                )
            }

            if (compact) {
                FloatingActionButton(
                    onClick = onClick,
                    shape = CircleShape,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    ),
                    modifier = Modifier
                        .size(fabSize)
                        .testTag(if (isRecording) "stop_record_button" else "record_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                LargeFloatingActionButton(
                    onClick = onClick,
                    shape = CircleShape,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    ),
                    modifier = Modifier
                        .size(fabSize)
                        .testTag(if (isRecording) "stop_record_button" else "record_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        if (!compact) {
            Text(
                text = if (isRecording) {
                    stringResource(R.string.action_stop_analyze)
                } else {
                    stringResource(R.string.tap_to_record)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (activeListening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandedRecordingContent(
    recordingState: RecordingState,
    recPulseAlpha: Float,
    recRingScale: Float,
    onCancelRecording: () -> Unit,
    onTogglePauseRecording: () -> Unit,
    onSaveDraft: () -> Unit,
    onStopRecording: () -> Unit
) {
    val latestAmplitude = recordingState.amplitudes.lastOrNull() ?: 0.08f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (recordingState.isPaused) 1f else recRingScale.coerceAtMost(1.25f))
                        .clip(CircleShape)
                        .background(
                            if (recordingState.isPaused) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error.copy(alpha = recPulseAlpha)
                        )
                )
                Text(
                    text = if (recordingState.isPaused) {
                        stringResource(R.string.status_paused)
                    } else {
                        stringResource(R.string.status_listening)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!recordingState.isPaused) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = recPulseAlpha),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Surface(
                color = if (recordingState.isPaused) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = formatDuration(recordingState.elapsedSeconds),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (recordingState.isPaused) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        AudioWaveformCanvas(
            amplitudes = recordingState.amplitudes,
            isPaused = recordingState.isPaused,
            height = 60.dp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                OutlinedButton(
                    onClick = onCancelRecording,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_record_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        modifier = Modifier.size(18.dp)
                    )
                }
                OutlinedButton(
                    onClick = onTogglePauseRecording,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pause_record_button")
                ) {
                    Icon(
                        imageVector = if (recordingState.isPaused) Icons.Rounded.PlayArrow
                        else Icons.Rounded.Pause,
                        contentDescription = if (recordingState.isPaused) {
                            stringResource(R.string.action_resume)
                        } else {
                            stringResource(R.string.action_pause)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (recordingState.isPaused) stringResource(R.string.action_resume)
                        else stringResource(R.string.action_pause),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            CentralVoiceToggleFab(
                isRecording = true,
                isPaused = recordingState.isPaused,
                compact = false,
                pulseScale = recRingScale,
                pulseAlpha = recPulseAlpha,
                latestAmplitude = latestAmplitude,
                onClick = onStopRecording
            )

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedButton(
                onClick = onSaveDraft,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_draft_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_save_draft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CompactRecordingContent(
    recordingState: RecordingState,
    recPulseAlpha: Float,
    recRingScale: Float = 1.15f,
    onCancelRecording: () -> Unit,
    onTogglePauseRecording: () -> Unit,
    onSaveDraft: (() -> Unit)? = null,
    onStopRecording: () -> Unit
) {
    val latestAmplitude = recordingState.amplitudes.lastOrNull() ?: 0.08f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onCancelRecording,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("cancel_record_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            color = if (recordingState.isPaused) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (recordingState.isPaused) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error.copy(alpha = recPulseAlpha)
                        )
                )
                Text(
                    text = formatDuration(recordingState.elapsedSeconds),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (recordingState.isPaused) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        AudioWaveformCanvas(
            amplitudes = recordingState.amplitudes.takeLast(10),
            isPaused = recordingState.isPaused,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )

        CentralVoiceToggleFab(
            isRecording = true,
            isPaused = recordingState.isPaused,
            compact = true,
            pulseScale = recRingScale,
            pulseAlpha = recPulseAlpha,
            latestAmplitude = latestAmplitude,
            onClick = onStopRecording
        )

        IconButton(
            onClick = onTogglePauseRecording,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("pause_record_button")
        ) {
            Icon(
                imageVector = if (recordingState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (recordingState.isPaused) stringResource(R.string.action_resume)
                else stringResource(R.string.action_pause),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (onSaveDraft != null) {
            IconButton(
                onClick = onSaveDraft,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_draft_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkAdd,
                    contentDescription = stringResource(R.string.action_save_draft),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    amplitudes: List<Float>,
    isPaused: Boolean,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val barPrimary = MaterialTheme.colorScheme.primary
    val barPeak = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (amplitudes.isNotEmpty()) {
            val slotWidth = size.width / amplitudes.size
            val barWidth = (slotWidth * 0.56f).coerceAtLeast(3f)
            val centerY = size.height / 2f
            val activeBrush = Brush.verticalGradient(colors = listOf(barPeak, barPrimary))
            amplitudes.forEachIndexed { index, amp ->
                val clamped = amp.coerceIn(0.08f, 1f)
                val barHeight = size.height * clamped
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val y = centerY - barHeight / 2f
                if (clamped > 0.24f && !isPaused) {
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                } else {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingPipelineContent(isSearching: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = if (isSearching) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isSearching) {
                    stringResource(R.string.status_fact_checking)
                } else {
                    stringResource(R.string.status_transcribing)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PipelineStepBadge(
                label = stringResource(R.string.pipeline_step_audio),
                done = true,
                active = false,
                modifier = Modifier.weight(1f)
            )
            PipelineStepBadge(
                label = stringResource(R.string.pipeline_step_transcribe),
                done = isSearching,
                active = !isSearching,
                modifier = Modifier.weight(1f)
            )
            PipelineStepBadge(
                label = stringResource(R.string.pipeline_step_verify),
                done = false,
                active = isSearching,
                modifier = Modifier.weight(1f)
            )
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isSearching) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PipelineStepBadge(
    label: String,
    done: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor: Color = when {
        done -> MaterialTheme.colorScheme.secondaryContainer
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val textColor: Color = when {
        done -> MaterialTheme.colorScheme.onSecondaryContainer
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

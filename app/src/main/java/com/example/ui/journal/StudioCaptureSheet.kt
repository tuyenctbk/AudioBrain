package com.example.ui.journal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_ring_scale"
    )
    val idleOuterRingAlpha by pulseTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_outer_alpha"
    )
    val recRingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_ring_scale"
    )
    val recPulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("import_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_import_short), style = MaterialTheme.typography.labelLarge)
            }

            PulsingRecordButton(
                idleRingScale = idleRingScale,
                idleOuterRingAlpha = idleOuterRingAlpha,
                compact = false,
                onClick = onStartRecording
            )

            OutlinedButton(
                onClick = onOpenTypeMemo,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("type_memo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_type_short), style = MaterialTheme.typography.labelLarge)
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

        PulsingRecordButton(
            idleRingScale = idleRingScale,
            idleOuterRingAlpha = idleOuterRingAlpha,
            compact = true,
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

@Composable
private fun PulsingRecordButton(
    idleRingScale: Float,
    idleOuterRingAlpha: Float,
    compact: Boolean,
    onClick: () -> Unit
) {
    val buttonHeight = if (compact) 48.dp else 60.dp
    val ringWidth = if (compact) 144.dp else 156.dp

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .height(buttonHeight)
                .width(ringWidth)
                .scale(idleRingScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = idleOuterRingAlpha))
        )
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = if (compact) 20.dp else 26.dp),
            modifier = Modifier
                .height(buttonHeight)
                .testTag("record_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.action_record),
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_record),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
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
                        stringResource(R.string.status_recording)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
            height = 64.dp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onCancelRecording,
                contentPadding = PaddingValues(horizontal = 12.dp),
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
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("pause_record_button")
            ) {
                Icon(
                    imageVector = if (recordingState.isPaused) Icons.Rounded.PlayArrow
                    else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (recordingState.isPaused) stringResource(R.string.action_resume)
                    else stringResource(R.string.action_pause),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onSaveDraft,
                contentPadding = PaddingValues(horizontal = 14.dp),
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
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!recordingState.isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(scaleX = 1.02f, scaleY = recRingScale.coerceAtMost(1.10f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                )
            }
            Button(
                onClick = onStopRecording,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("stop_record_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_stop_analyze))
            }
        }
    }
}

@Composable
private fun CompactRecordingContent(
    recordingState: RecordingState,
    recPulseAlpha: Float,
    onCancelRecording: () -> Unit,
    onTogglePauseRecording: () -> Unit,
    onSaveDraft: (() -> Unit)? = null,
    onStopRecording: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (recordingState.isPaused) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error.copy(alpha = recPulseAlpha)
                )
        )
        Surface(
            color = if (recordingState.isPaused) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = formatDuration(recordingState.elapsedSeconds),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (recordingState.isPaused) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        AudioWaveformCanvas(
            amplitudes = recordingState.amplitudes.takeLast(14),
            isPaused = recordingState.isPaused,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )

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

        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onStopRecording)
                .testTag("stop_record_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.action_stop_analyze),
                    tint = MaterialTheme.colorScheme.onPrimary
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

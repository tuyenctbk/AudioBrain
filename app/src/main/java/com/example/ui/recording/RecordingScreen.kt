package com.example.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.RecordingState
import com.example.ui.journal.CapturePhase
import com.example.ui.journal.JournalViewModel
import com.example.ui.journal.PlayableJournalAudio
import java.util.Locale

@Composable
fun RecordingScreenRoute(
    viewModel: JournalViewModel,
    onNoteCreated: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val playableAudios by viewModel.playableJournalAudios.collectAsStateWithLifecycle()
    val playingAudioKey by viewModel.playingAudioKey.collectAsStateWithLifecycle()
    val playbackState by viewModel.draftPlaybackState.collectAsStateWithLifecycle()
    var selectedAudioKey by remember { mutableStateOf<String?>(null) }
    var showMicRationaleDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioPlayback()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.createdNoteEvents.collect { noteId ->
            onNoteCreated(noteId)
        }
    }

    LaunchedEffect(playableAudios, playingAudioKey) {
        if (playingAudioKey != null) {
            selectedAudioKey = playingAudioKey
        } else if (selectedAudioKey == null || playableAudios.none { it.key == selectedAudioKey }) {
            selectedAudioKey = playableAudios.firstOrNull()?.key
        }
    }

    BackHandler(onBack = onBack)

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.startRecording()
        } else {
            showMicRationaleDialog = true
        }
    }

    val handleToggleRecording: () -> Unit = {
        if (recordingState.isRecording) {
            viewModel.stopAndProcessRecording()
        } else {
            val micGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                viewModel.startRecording()
            } else {
                val permissions = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
                recordPermissionLauncher.launch(permissions)
            }
        }
    }

    RecordingScreen(
        recordingState = recordingState,
        capturePhase = captureState.phase,
        autoFactCheck = captureState.autoFactCheck,
        errorMessage = captureState.errorMessage ?: recordingState.errorMessage,
        infoMessage = captureState.infoMessage,
        playableAudios = playableAudios,
        selectedAudioKey = selectedAudioKey,
        activePlaybackKey = playingAudioKey,
        playbackState = playbackState,
        onToggleRecording = handleToggleRecording,
        onTogglePauseRecording = viewModel::togglePauseRecording,
        onSaveDraft = viewModel::stopAndSaveForLater,
        onCancelRecording = viewModel::cancelRecording,
        onToggleAutoFactCheck = viewModel::setAutoFactCheck,
        onSelectAudio = { item ->
            selectedAudioKey = item.key
            if (!recordingState.isRecording) {
                viewModel.togglePlayRecordedAudio(item)
            }
        },
        onTogglePlayAudio = viewModel::togglePlayRecordedAudio,
        onSeekPlayback = viewModel::seekDraftPlayback,
        onCyclePlaybackSpeed = viewModel::cycleDraftPlaybackSpeed,
        onDismissMessage = viewModel::clearCaptureMessages,
        onBack = onBack,
        modifier = modifier
    )

    if (showMicRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showMicRationaleDialog = false },
            title = { Text(stringResource(R.string.mic_permission_title)) },
            text = { Text(stringResource(R.string.mic_permission_body)) },
            confirmButton = {
                Button(onClick = {
                    showMicRationaleDialog = false
                    recordPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }) {
                    Text(stringResource(R.string.mic_permission_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationaleDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun RecordingScreen(
    recordingState: RecordingState,
    capturePhase: CapturePhase = CapturePhase.Idle,
    autoFactCheck: Boolean = true,
    errorMessage: String? = null,
    infoMessage: String? = null,
    playableAudios: List<PlayableJournalAudio> = emptyList(),
    selectedAudioKey: String? = null,
    activePlaybackKey: String? = null,
    playbackState: AudioPlaybackState = AudioPlaybackState(),
    onToggleRecording: () -> Unit,
    onTogglePauseRecording: () -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onToggleAutoFactCheck: (Boolean) -> Unit = {},
    onSelectAudio: (PlayableJournalAudio) -> Unit = {},
    onTogglePlayAudio: (PlayableJournalAudio) -> Unit = {},
    onSeekPlayback: (Long) -> Unit = {},
    onCyclePlaybackSpeed: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isActiveRecording = recordingState.isRecording
    val isPaused = recordingState.isPaused
    val isListening = isActiveRecording && !isPaused
    val isProcessing = capturePhase == CapturePhase.Transcribing || capturePhase == CapturePhase.FactChecking
    val latestAmplitude = recordingState.amplitudes.lastOrNull() ?: 0.08f

    val pulseTransition = rememberInfiniteTransition(label = "recording_screen_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.36f else 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 820 else 1550,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = if (isListening) 0.42f else 0.22f,
        targetValue = if (isListening) 0.12f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 820 else 1550,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_alpha"
    )
    val statusDotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_alpha"
    )
    val amplitudeBoost by animateFloatAsState(
        targetValue = if (isListening) 1f + (latestAmplitude.coerceIn(0.08f, 1f) * 0.26f) else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mic_amplitude_boost"
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            !isActiveRecording -> MaterialTheme.colorScheme.primary
            isPaused -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(260),
        label = "recording_accent_color"
    )
    val accentOnColor by animateColorAsState(
        targetValue = when {
            !isActiveRecording -> MaterialTheme.colorScheme.onPrimary
            isPaused -> MaterialTheme.colorScheme.onTertiary
            else -> MaterialTheme.colorScheme.onError
        },
        animationSpec = tween(260),
        label = "recording_on_accent_color"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Header bar
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("recording_screen_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.detail_back_to_journal),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = stringResource(R.string.shortcut_record_long),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = when {
                            isListening -> MaterialTheme.colorScheme.errorContainer
                            isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isListening -> MaterialTheme.colorScheme.error.copy(alpha = statusDotAlpha)
                                            isPaused -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    isListening -> stringResource(R.string.status_listening)
                                    isPaused -> stringResource(R.string.status_paused)
                                    else -> stringResource(R.string.action_record)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = when {
                                    isListening -> MaterialTheme.colorScheme.onErrorContainer
                                    isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                // Error or status banner
                val bannerText = errorMessage ?: infoMessage
                val isError = errorMessage != null
                AnimatedVisibility(visible = bannerText != null) {
                    Surface(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = bannerText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissMessage,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_dismiss_message),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Timer & Audio Waveform Stage
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isListening) accentColor.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = formatRecordingDuration(recordingState.elapsedSeconds),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("recording_timer_text")
                        )

                        RecordingWaveformCanvas(
                            amplitudes = recordingState.amplitudes,
                            isListening = isListening,
                            isPaused = isPaused,
                            height = 112.dp,
                            modifier = Modifier.testTag("recording_waveform_canvas")
                        )

                        AnimatedContent(
                            targetState = Triple(isActiveRecording, isPaused, isProcessing),
                            transitionSpec = {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(180))
                            },
                            label = "recording_stage_caption"
                        ) { (rec, paused, processing) ->
                            when {
                                processing -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.5.dp,
                                                color = if (capturePhase == CapturePhase.FactChecking) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            Text(
                                                text = if (capturePhase == CapturePhase.FactChecking) {
                                                    stringResource(R.string.status_fact_checking)
                                                } else {
                                                    stringResource(R.string.status_transcribing)
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }
                                rec && !paused -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.status_recording),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                rec && paused -> {
                                    Text(
                                        text = stringResource(R.string.status_paused),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {
                                    Text(
                                        text = stringResource(R.string.tap_to_record),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Central Prominent Microphone Toggle with Multi-Ring Pulse Animation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(176.dp)
                    ) {
                        if (!isPaused) {
                            // Outer pulsing acoustic ring
                            Box(
                                modifier = Modifier
                                    .size(164.dp)
                                    .scale(pulseScale * amplitudeBoost)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = pulseAlpha * 0.55f))
                            )
                            // Inner acoustic resonance ring
                            Box(
                                modifier = Modifier
                                    .size(128.dp)
                                    .scale(((pulseScale + 1f) / 2f) * amplitudeBoost)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = (pulseAlpha * 0.95f).coerceAtMost(0.45f)))
                            )
                        }

                        Surface(
                            onClick = {
                                if (!isProcessing) onToggleRecording()
                            },
                            enabled = !isProcessing,
                            shape = CircleShape,
                            color = accentColor,
                            contentColor = accentOnColor,
                            shadowElevation = if (isListening) 12.dp else 6.dp,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .size(96.dp)
                                .testTag("recording_toggle_mic_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isActiveRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = stringResource(
                                        if (isActiveRecording) R.string.action_stop_analyze else R.string.action_record
                                    ),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(
                            if (isActiveRecording) R.string.action_stop_analyze else R.string.action_record
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Bottom Action Area (Active recording actions OR Idle auto-fact-check toggle)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp)
                ) {
                    if (isActiveRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onCancelRecording,
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("recording_screen_cancel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalButton(
                                onClick = onTogglePauseRecording,
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("recording_screen_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (isPaused) R.string.action_resume else R.string.action_pause
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSaveDraft,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("recording_screen_save_draft_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_save_draft),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_fact_check_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = autoFactCheck,
                                    onCheckedChange = onToggleAutoFactCheck,
                                    modifier = Modifier.testTag("recording_screen_auto_fact_check_switch")
                                )
                            }
                        }
                    }

                    val selectedAudio = playableAudios.firstOrNull { it.key == selectedAudioKey }
                        ?: playableAudios.firstOrNull { it.key == activePlaybackKey }
                        ?: playableAudios.firstOrNull()
                    if (selectedAudio != null) {
                        RecordedAudioPlayerCard(
                            playableAudios = playableAudios,
                            selectedAudio = selectedAudio,
                            activePlaybackKey = activePlaybackKey,
                            playbackState = playbackState,
                            isRecordingActive = isActiveRecording,
                            onSelectAudio = onSelectAudio,
                            onTogglePlayPause = { onTogglePlayAudio(selectedAudio) },
                            onSeek = onSeekPlayback,
                            onCycleSpeed = onCyclePlaybackSpeed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordedAudioPlayerCard(
    playableAudios: List<PlayableJournalAudio>,
    selectedAudio: PlayableJournalAudio,
    activePlaybackKey: String?,
    playbackState: AudioPlaybackState,
    isRecordingActive: Boolean,
    onSelectAudio: (PlayableJournalAudio) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThisLoaded = activePlaybackKey == selectedAudio.key && playbackState.hasAudio
    val isPlayingThis = isThisLoaded && playbackState.isPlaying
    val isPlayEnabled = !isRecordingActive && (!isThisLoaded || playbackState.isPrepared)

    val totalDurationMs = if (isThisLoaded && playbackState.durationMs > 0L) {
        playbackState.durationMs
    } else {
        (selectedAudio.durationSeconds.coerceAtLeast(1L)) * 1000L
    }
    val currentPosMs = if (isThisLoaded) {
        playbackState.currentPositionMs.coerceIn(0L, totalDurationMs)
    } else {
        0L
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlayingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("recording_audio_player_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = onTogglePlayPause,
                    enabled = isPlayEnabled,
                    shape = CircleShape,
                    color = if (isPlayingThis) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("recording_player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlayingThis) R.string.cd_pause_audio else R.string.cd_play_audio
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = selectedAudio.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("recording_player_title")
                    )
                    Text(
                        text = if (isPlayingThis) {
                            stringResource(R.string.audio_playing_recording)
                        } else {
                            stringResource(R.string.audio_local_recording)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlayingThis) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = onCycleSpeed,
                    enabled = !isRecordingActive,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recording_player_speed_button")
                ) {
                    Text(
                        text = "${playbackState.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = currentPosMs.toFloat(),
                    onValueChange = { pos ->
                        if (isThisLoaded && playbackState.isPrepared) {
                            onSeek(pos.toLong())
                        }
                    },
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    enabled = isThisLoaded && playbackState.isPrepared && !isRecordingActive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recording_player_slider")
                )
                Text(
                    text = "${formatRecordingDuration(currentPosMs / 1000L)} / ${formatRecordingDuration(totalDurationMs / 1000L)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (playableAudios.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(playableAudios, key = { it.key }) { item ->
                        val isSelected = item.key == selectedAudio.key
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectAudio(item) },
                            enabled = !isRecordingActive,
                            label = {
                                Text(
                                    text = "${item.title} • ${formatRecordingDuration(item.durationSeconds)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            if (isThisLoaded && playbackState.errorMessage != null) {
                Text(
                    text = playbackState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RecordingWaveformCanvas(
    amplitudes: List<Float>,
    isListening: Boolean,
    isPaused: Boolean,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val barPrimary = MaterialTheme.colorScheme.primary
    val barPeak = MaterialTheme.colorScheme.error
    val barAccent = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (amplitudes.isEmpty()) return@Canvas
        val slotWidth = size.width / amplitudes.size
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(4f)
        val centerY = size.height / 2f
        val liveBrush = Brush.verticalGradient(
            colors = listOf(barPeak, barPrimary, barAccent)
        )
        val pausedBrush = Brush.verticalGradient(
            colors = listOf(barAccent.copy(alpha = 0.55f), barAccent.copy(alpha = 0.28f))
        )

        amplitudes.forEachIndexed { index, rawAmp ->
            val clamped = rawAmp.coerceIn(0.08f, 1f)
            val barHeight = size.height * clamped
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barHeight / 2f
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            when {
                isListening && clamped > 0.12f -> {
                    drawRoundRect(
                        brush = liveBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
                isPaused && clamped > 0.12f -> {
                    drawRoundRect(
                        brush = pausedBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
                else -> {
                    drawRoundRect(
                        color = mutedColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

internal fun formatRecordingDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val mins = safe / 60L
    val secs = safe % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

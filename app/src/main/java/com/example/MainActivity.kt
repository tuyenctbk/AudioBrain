package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.LocalAudioStore
import com.example.audio.RecordingForegroundService
import com.example.audio.VoiceRecorderManager
import com.example.core.configureAppCheck
import com.example.data.ai.AudioBrainAiService
import com.example.data.billing.UsageQuotaManager
import com.example.data.offline.NetworkConnectivityObserver
import com.example.data.offline.PendingMemoStore
import com.example.data.repository.VoiceNotesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.detail.NoteDetailScreen
import com.example.ui.detail.NoteDetailViewModel
import com.example.ui.journal.JournalScreen
import com.example.ui.journal.JournalViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.review.TranscriptReviewScreen
import com.example.ui.review.TranscriptReviewViewModel
import com.example.ui.theme.AudioBrainTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_JOURNAL = "journal"
const val ROUTE_NOTE_DETAIL = "note/{noteId}"
const val ROUTE_TRANSCRIPT_REVIEW = "note/{noteId}/review"
const val ACTION_QUICK_RECORD = "com.aistudio.audiobrain.ACTION_QUICK_RECORD"
const val EXTRA_OPEN_NOTE_ID = "com.aistudio.audiobrain.EXTRA_OPEN_NOTE_ID"

sealed interface ExternalLaunchCommand {
    data class QuickRecord(val token: Long = System.nanoTime()) : ExternalLaunchCommand
    data class ImportAudioUri(val uri: Uri, val token: Long = System.nanoTime()) : ExternalLaunchCommand
    data class OpenNote(val noteId: String, val token: Long = System.nanoTime()) : ExternalLaunchCommand
}

class MainActivity : ComponentActivity() {
    private val externalCommandFlow = MutableStateFlow<ExternalLaunchCommand?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Must execute before setContent, ViewModels, or any Firebase operations
        configureAppCheck(this, intent)
        handleIncomingIntent(intent)

        setContent {
            AudioBrainTheme {
                val externalCommand by externalCommandFlow.collectAsStateWithLifecycle()
                AudioBrainRoot(
                    externalCommand = externalCommand,
                    onConsumeExternalCommand = { externalCommandFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val openNoteId = intent.getStringExtra(EXTRA_OPEN_NOTE_ID)?.trim()
        if (!openNoteId.isNullOrBlank()) {
            externalCommandFlow.value = ExternalLaunchCommand.OpenNote(openNoteId)
            return
        }
        when (intent.action) {
            ACTION_QUICK_RECORD -> {
                externalCommandFlow.value = ExternalLaunchCommand.QuickRecord()
            }
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                if (streamUri != null) {
                    externalCommandFlow.value = ExternalLaunchCommand.ImportAudioUri(streamUri)
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun AudioBrainRoot(
    externalCommand: ExternalLaunchCommand?,
    onConsumeExternalCommand: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val context = LocalContext.current
    val quotaManager = remember(context) { UsageQuotaManager.getInstance(context) }
    val hasCompletedOnboarding by quotaManager.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var manualSignedInUser by remember { mutableStateOf(auth.currentUser) }
    var isGuestMode by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        manualSignedInUser = currentUser
        if (currentUser != null) {
            isGuestMode = false
        }
    }

    val activeFirebaseUser = currentUser ?: manualSignedInUser
    val effectiveUid = activeFirebaseUser?.uid ?: if (isGuestMode) VoiceNotesRepository.GUEST_USER_ID else null

    LaunchedEffect(effectiveUid, activeFirebaseUser) {
        quotaManager.setAuthContext(
            userId = effectiveUid,
            isGuest = activeFirebaseUser == null
        )
    }

    val rootDestination = when {
        !hasCompletedOnboarding -> "onboarding"
        effectiveUid == null -> "auth"
        else -> "app:$effectiveUid"
    }

    Crossfade(
        targetState = rootDestination,
        animationSpec = tween(durationMillis = 280),
        label = "root_gate_crossfade"
    ) { destination ->
        val user = activeFirebaseUser
        when {
            destination == "onboarding" -> {
                OnboardingScreen(
                    onFinishOnboarding = { quotaManager.completeOnboarding() }
                )
            }
            destination == "auth" || effectiveUid == null -> {
                AuthScreen(
                    onAuthSuccess = {
                        val fbUser = auth.currentUser
                        manualSignedInUser = fbUser
                        if (fbUser == null) {
                            isGuestMode = true
                        } else {
                            isGuestMode = false
                        }
                    },
                    onContinueAsGuest = {
                        isGuestMode = true
                    }
                )
            }
            else -> {
                key(effectiveUid) {
                    AuthenticatedAudioBrainNav(
                        currentUserId = effectiveUid,
                        userDisplayName = user?.displayName ?: stringResource(R.string.auth_guest_display_name),
                        userEmail = user?.email ?: "guest@audiobrain.ai",
                        externalCommand = externalCommand,
                        onConsumeExternalCommand = onConsumeExternalCommand,
                        onSignedOut = {
                            isGuestMode = false
                            manualSignedInUser = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedAudioBrainNav(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?,
    externalCommand: ExternalLaunchCommand?,
    onConsumeExternalCommand: () -> Unit,
    onSignedOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val sharedRecorder = remember(appContext) { VoiceRecorderManager.shared(appContext) }
    val sharedPendingStore = remember(appContext) { PendingMemoStore.getInstance(appContext) }
    val activeRecordingState by sharedRecorder.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_JOURNAL,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(280)
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(260),
                targetOffset = { it / 4 }
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(260),
                initialOffset = { it / 4 }
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(280)
            ) + fadeOut(animationSpec = tween(200))
        }
    ) {
        composable(ROUTE_JOURNAL) {
            val journalViewModel: JournalViewModel = viewModel(
                key = "journal_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        JournalViewModel(
                            appContext = app,
                            repository = VoiceNotesRepository(db, appContext = app.applicationContext),
                            aiService = AudioBrainAiService(app),
                            recorderManager = VoiceRecorderManager.shared(app),
                            pendingStore = PendingMemoStore.getInstance(app),
                            localAudioStore = LocalAudioStore(app),
                            networkObserver = NetworkConnectivityObserver(app),
                            currentUserId = currentUserId
                        )
                    }
                }
            )

            JournalScreen(
                viewModel = journalViewModel,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                externalCommand = externalCommand,
                onConsumeExternalCommand = onConsumeExternalCommand,
                onNoteClick = { noteId ->
                    navController.navigate("note/$noteId") {
                        launchSingleTop = true
                    }
                },
                onReviewNoteClick = { noteId ->
                    navController.navigate("note/$noteId/review") {
                        launchSingleTop = true
                    }
                },
                onSignOutClick = {
                    journalViewModel.stopActiveRecordingForSignOut()
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = onSignedOut
                    )
                }
            )
        }

        composable(
            route = ROUTE_NOTE_DETAIL,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            val detailViewModel: NoteDetailViewModel = viewModel(
                key = "detail_${currentUserId}_$noteId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        NoteDetailViewModel(
                            appContext = app,
                            repository = VoiceNotesRepository(db, appContext = app.applicationContext),
                            aiService = AudioBrainAiService(app),
                            localAudioStore = LocalAudioStore(app),
                            recorderManager = VoiceRecorderManager.shared(app),
                            currentUserId = currentUserId,
                            noteId = noteId
                        )
                    }
                }
            )

            NoteDetailScreen(
                viewModel = detailViewModel,
                recordingState = activeRecordingState,
                onTogglePauseRecording = {
                    val current = sharedRecorder.state.value
                    if (current.isRecording) {
                        if (current.isPaused) {
                            sharedRecorder.resumeRecording(scope)
                        } else {
                            sharedRecorder.pauseRecording()
                        }
                    }
                },
                onStopRecording = {
                    scope.launch {
                        RecordingForegroundService.stop(appContext)
                        sharedRecorder.stopRecording().onSuccess { recorded ->
                            sharedPendingStore.enqueueAudio(
                                userId = currentUserId,
                                bytes = recorded.bytes,
                                mimeType = recorded.mimeType,
                                durationSeconds = recorded.durationSeconds,
                                autoFactCheck = sharedRecorder.sessionAutoFactCheck
                            )
                        }
                    }
                },
                onSaveDraft = {
                    scope.launch {
                        RecordingForegroundService.stop(appContext)
                        sharedRecorder.stopRecording().onSuccess { recorded ->
                            val timeLabel = java.text.SimpleDateFormat(
                                "MMM d • h:mm a",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                            sharedPendingStore.enqueueAudio(
                                userId = currentUserId,
                                bytes = recorded.bytes,
                                mimeType = recorded.mimeType,
                                durationSeconds = recorded.durationSeconds,
                                autoFactCheck = sharedRecorder.sessionAutoFactCheck,
                                deferredForLater = true,
                                label = appContext.getString(R.string.voice_note_label_with_time, timeLabel)
                            )
                        }
                    }
                },
                onCancelRecording = {
                    sharedRecorder.cancelRecording()
                    RecordingForegroundService.stop(appContext)
                },
                onOpenTranscriptReview = {
                    navController.navigate("note/$noteId/review") {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_TRANSCRIPT_REVIEW,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            val reviewViewModel: TranscriptReviewViewModel = viewModel(
                key = "review_${currentUserId}_$noteId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        TranscriptReviewViewModel(
                            appContext = app,
                            repository = VoiceNotesRepository(db, appContext = app.applicationContext),
                            aiService = AudioBrainAiService(app),
                            localAudioStore = LocalAudioStore(app),
                            currentUserId = currentUserId,
                            noteId = noteId
                        )
                    }
                }
            )

            TranscriptReviewScreen(
                viewModel = reviewViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

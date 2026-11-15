package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.offline.PendingMemoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class RecordingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    private val recorderManager by lazy { VoiceRecorderManager.shared(applicationContext) }
    private val pendingStore by lazy { PendingMemoStore.getInstance(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceActive = true
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                promoteToForeground(buildNotification(recorderManager.state.value))
                if (pendingStopRequested || !recorderManager.state.value.isRecording) {
                    stopSelfSafely()
                } else {
                    observeRecordingUpdates()
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                val current = recorderManager.state.value
                if (current.isRecording) {
                    if (current.isPaused) {
                        recorderManager.resumeRecording(serviceScope)
                    } else {
                        recorderManager.pauseRecording()
                    }
                    updateNotification(recorderManager.state.value)
                } else {
                    stopSelfSafely()
                }
            }
            ACTION_STOP_AND_SAVE, ACTION_STOP_AND_SAVE_DRAFT -> {
                val saveAsDraft = intent.action == ACTION_STOP_AND_SAVE_DRAFT
                serviceScope.launch {
                    val uid = recorderManager.sessionUserId
                    val autoVerify = recorderManager.sessionAutoFactCheck
                    val result = recorderManager.stopRecording()
                    result.onSuccess { recorded ->
                        if (!uid.isNullOrBlank()) {
                            pendingStore.enqueueAudio(
                                userId = uid,
                                bytes = recorded.bytes,
                                mimeType = recorded.mimeType,
                                durationSeconds = recorded.durationSeconds,
                                autoFactCheck = autoVerify,
                                deferredForLater = saveAsDraft,
                                label = if (saveAsDraft) buildDefaultDraftLabel() else null
                            )
                        }
                    }
                    stopSelfSafely()
                }
            }
            ACTION_CANCEL -> {
                recorderManager.cancelRecording()
                stopSelfSafely()
            }
            ACTION_STOP_SERVICE_ONLY -> {
                stopSelfSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeRecordingUpdates() {
        if (observerJob?.isActive == true) return
        var lastSecond = -1L
        var lastPaused: Boolean? = null

        observerJob = serviceScope.launch {
            recorderManager.state.collectLatest { state ->
                if (!state.isRecording) {
                    stopSelfSafely()
                    return@collectLatest
                }
                if (state.elapsedSeconds != lastSecond || state.isPaused != lastPaused) {
                    lastSecond = state.elapsedSeconds
                    lastPaused = state.isPaused
                    updateNotification(state)
                }
            }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: RecordingState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: RecordingState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseActionIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pausePending = PendingIntent.getService(
            this,
            1,
            pauseActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val saveDraftIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP_AND_SAVE_DRAFT
        }
        val savePending = PendingIntent.getService(
            this,
            2,
            saveDraftIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelActionIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            3,
            cancelActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = String.format(
            Locale.US,
            "%02d:%02d",
            state.elapsedSeconds / 60L,
            state.elapsedSeconds % 60L
        )
        val statusTitle = if (state.isPaused) {
            getString(R.string.notif_title_paused, formattedTime)
        } else {
            getString(R.string.notif_title_recording, formattedTime)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(statusTitle)
            .setContentText(getString(R.string.notif_content_text))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (state.isPaused) getString(R.string.action_resume) else getString(R.string.action_pause),
                pausePending
            )
            .addAction(0, getString(R.string.action_save_draft), savePending)
            .addAction(0, getString(R.string.action_cancel), cancelPending)
            .build()
    }

    private fun buildDefaultDraftLabel(): String {
        val timeFmt = java.text.SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
        return getString(R.string.draft_title_with_time, timeFmt.format(java.util.Date()))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopSelfSafely() {
        observerJob?.cancel()
        observerJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isServiceActive = false
        pendingStopRequested = false
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "audiobrain_recording"
        private const val NOTIFICATION_ID = 4201

        @Volatile
        private var isServiceActive = false

        @Volatile
        private var pendingStopRequested = false

        const val ACTION_START = "com.example.audio.ACTION_START_RECORDING"
        const val ACTION_TOGGLE_PAUSE = "com.example.audio.ACTION_TOGGLE_PAUSE"
        const val ACTION_STOP_AND_SAVE = "com.example.audio.ACTION_STOP_AND_SAVE"
        const val ACTION_STOP_AND_SAVE_DRAFT = "com.example.audio.ACTION_STOP_AND_SAVE_DRAFT"
        const val ACTION_CANCEL = "com.example.audio.ACTION_CANCEL"
        const val ACTION_STOP_SERVICE_ONLY = "com.example.audio.ACTION_STOP_SERVICE_ONLY"

        fun start(context: Context) {
            pendingStopRequested = false
            val intent = Intent(context.applicationContext, RecordingForegroundService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }
        }

        fun stop(context: Context) {
            pendingStopRequested = true
            if (isServiceActive) {
                runCatching {
                    context.applicationContext.stopService(
                        Intent(context.applicationContext, RecordingForegroundService::class.java)
                    )
                }
            }
        }
    }
}

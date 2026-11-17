package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val amplitudes: List<Float> = List(36) { 0.08f },
    val errorMessage: String? = null
)

data class RecordedAudioResult(
    val bytes: ByteArray,
    val mimeType: String,
    val durationSeconds: Long
)

class VoiceRecorderManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val recorderLock = Any()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var activeSegmentStartMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private var meterJob: Job? = null

    @Volatile
    var sessionUserId: String? = null
        private set

    @Volatile
    var sessionAutoFactCheck: Boolean = true
        private set

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun startRecording(
        scope: CoroutineScope,
        userId: String? = null,
        autoFactCheck: Boolean = true
    ): Boolean = synchronized(recorderLock) {
        if (_state.value.isRecording) return false
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            _state.value = RecordingState(
                isRecording = false,
                isPaused = false,
                errorMessage = appContext.getString(R.string.err_mic_permission_record)
            )
            return false
        }

        cleanupRecorder()
        requestRecordingAudioFocus()

        sessionUserId = userId
        sessionAutoFactCheck = autoFactCheck

        val file = File(appContext.cacheDir, "audiobrain_rec_${System.currentTimeMillis()}.m4a")
        return try {
            val mediaRecorder = createAndStartMediaRecorder(
                file = file,
                audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
            ) ?: run {
                file.delete()
                createAndStartMediaRecorder(
                    file = file,
                    audioSource = MediaRecorder.AudioSource.MIC
                )
            } ?: throw IllegalStateException(appContext.getString(R.string.err_mic_init_failed))

            recorder = mediaRecorder
            outputFile = file
            accumulatedDurationMs = 0L
            activeSegmentStartMs = System.currentTimeMillis()
            _state.value = RecordingState(
                isRecording = true,
                isPaused = false,
                elapsedSeconds = 0L,
                amplitudes = List(36) { 0.08f },
                errorMessage = null
            )

            startMeteringLoop(scope)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            cleanupRecorder()
            file.delete()
            sessionUserId = null
            sessionAutoFactCheck = true
            _state.value = RecordingState(
                isRecording = false,
                isPaused = false,
                errorMessage = e.localizedMessage ?: appContext.getString(R.string.err_mic_access_failed)
            )
            false
        }
    }

    fun pauseRecording(): Boolean = synchronized(recorderLock) {
        val current = _state.value
        val activeRecorder = recorder ?: return false
        if (!current.isRecording || current.isPaused) return false

        return try {
            activeRecorder.pause()
            accumulatedDurationMs += (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
            _state.value = current.copy(
                isPaused = true,
                elapsedSeconds = accumulatedDurationMs / 1000L
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pause MediaRecorder", e)
            false
        }
    }

    fun resumeRecording(scope: CoroutineScope): Boolean = synchronized(recorderLock) {
        val current = _state.value
        val activeRecorder = recorder ?: return false
        if (!current.isRecording || !current.isPaused) return false

        return try {
            activeRecorder.resume()
            activeSegmentStartMs = System.currentTimeMillis()
            _state.value = current.copy(isPaused = false)
            if (meterJob?.isActive != true) {
                startMeteringLoop(scope)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resume MediaRecorder", e)
            false
        }
    }

    private fun startMeteringLoop(scope: CoroutineScope) {
        meterJob?.cancel()
        meterJob = scope.launch(Dispatchers.Default) {
            while (isActive && _state.value.isRecording) {
                val activeRecorder = synchronized(recorderLock) { recorder } ?: break
                val current = _state.value
                if (current.isPaused) {
                    delay(120L)
                    continue
                }
                val rawAmp = try {
                    activeRecorder.maxAmplitude
                } catch (_: Exception) {
                    0
                }
                val normalized = min(1f, (rawAmp / 16000f)).coerceAtLeast(0.08f)
                val totalMs = accumulatedDurationMs + (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
                val latest = _state.value
                if (latest.isRecording && !latest.isPaused) {
                    _state.value = latest.copy(
                        elapsedSeconds = totalMs / 1000L,
                        amplitudes = (latest.amplitudes.drop(1) + normalized)
                    )
                }
                delay(65L)
            }
        }
    }

    suspend fun stopRecording(): Result<RecordedAudioResult> = withContext(Dispatchers.IO) {
        val currentRecorder: MediaRecorder?
        val file: File?
        val duration: Long
        synchronized(recorderLock) {
            currentRecorder = recorder
            file = outputFile
            recorder = null
            outputFile = null
            val wasPaused = _state.value.isPaused
            val finalDurationMs = if (wasPaused) {
                accumulatedDurationMs
            } else {
                accumulatedDurationMs + (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
            }
            duration = (finalDurationMs / 1000L).coerceAtLeast(1L)
            meterJob?.cancel()
            meterJob = null
            _state.value = RecordingState(isRecording = false, isPaused = false)
        }

        if (currentRecorder == null || file == null) {
            synchronized(recorderLock) { cleanupRecorder() }
            return@withContext Result.failure(IllegalStateException(appContext.getString(R.string.err_no_active_recording)))
        }

        try {
            currentRecorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder stop failed", e)
            runCatching { currentRecorder.reset() }
            runCatching { currentRecorder.release() }
            synchronized(recorderLock) { cleanupRecorder() }
            file.delete()
            return@withContext Result.failure(
                IllegalStateException(appContext.getString(R.string.err_recording_too_short))
            )
        } finally {
            runCatching { currentRecorder.reset() }
            runCatching { currentRecorder.release() }
            synchronized(recorderLock) { cleanupRecorder() }
        }

        return@withContext try {
            if (!file.exists() || file.length() == 0L) {
                Result.failure(IllegalStateException(appContext.getString(R.string.err_recorded_file_empty)))
            } else if (file.length() > MAX_INLINE_AUDIO_BYTES) {
                file.delete()
                Result.failure(IllegalStateException(appContext.getString(R.string.err_audio_too_large)))
            } else {
                val bytes = file.readBytes()
                file.delete()
                Result.success(
                    RecordedAudioResult(
                        bytes = bytes,
                        mimeType = "audio/mp4",
                        durationSeconds = duration
                    )
                )
            }
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }

    fun cancelRecording() = synchronized(recorderLock) {
        meterJob?.cancel()
        meterJob = null
        val activeRecorder = recorder
        recorder = null
        try {
            activeRecorder?.stop()
        } catch (_: Exception) {
        }
        runCatching { activeRecorder?.reset() }
        runCatching { activeRecorder?.release() }
        cleanupRecorder()
        outputFile?.delete()
        outputFile = null
        accumulatedDurationMs = 0L
        sessionUserId = null
        sessionAutoFactCheck = true
        _state.value = RecordingState()
    }

    suspend fun readImportedAudio(uri: Uri): Result<RecordedAudioResult> = withContext(Dispatchers.IO) {
        try {
            val mimeType = appContext.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") }
                ?: "audio/mp4"

            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var totalBytes = 0

            val opened = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    if (totalBytes > MAX_INLINE_AUDIO_BYTES) {
                        return@withContext Result.failure(
                            IllegalStateException(appContext.getString(R.string.err_import_file_too_large))
                        )
                    }
                    output.write(buffer, 0, read)
                }
                true
            } ?: false

            if (!opened) {
                return@withContext Result.failure(IllegalStateException(appContext.getString(R.string.err_import_read_failed)))
            }

            val bytes = output.toByteArray()
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException(appContext.getString(R.string.err_import_file_empty)))
            }

            val durationSeconds = extractAudioDurationSeconds(uri)
            Result.success(
                RecordedAudioResult(
                    bytes = bytes,
                    mimeType = mimeType,
                    durationSeconds = durationSeconds
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import audio URI", e)
            Result.failure(e)
        }
    }

    private fun extractAudioDurationSeconds(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            (durationMs / 1000L).coerceIn(0L, 86400L)
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun createAndStartMediaRecorder(file: File, audioSource: Int): MediaRecorder? {
        val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            candidate.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            candidate
        } catch (e: Exception) {
            Log.w(TAG, "AudioSource $audioSource initialization failed, trying fallback if available", e)
            runCatching { candidate.reset() }
            runCatching { candidate.release() }
            null
        }
    }

    private fun requestRecordingAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { /* handle transient focus change if needed */ }
                    .build()
                audioFocusRequest = req
                manager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to request audio focus for recording: ${e.message}")
        }
    }

    private fun abandonRecordingAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { req ->
                    manager.abandonAudioFocusRequest(req)
                }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
        audioFocusRequest = null
    }

    private fun cleanupRecorder() {
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        abandonRecordingAudioFocus()
    }

    companion object {
        private const val TAG = "VoiceRecorderManager"
        private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024

        @Volatile
        private var SHARED_INSTANCE: VoiceRecorderManager? = null

        fun shared(context: Context): VoiceRecorderManager {
            return SHARED_INSTANCE ?: synchronized(this) {
                SHARED_INSTANCE ?: VoiceRecorderManager(context.applicationContext).also {
                    SHARED_INSTANCE = it
                }
            }
        }
    }
}

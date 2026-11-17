package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
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
import java.io.File

data class AudioPlaybackState(
    val hasAudio: Boolean = false,
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
)

class AudioPlayerManager(
    private val scope: CoroutineScope,
    private val appContext: Context? = null
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var loadedPath: String? = null

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    fun loadFile(file: File?, autoPlay: Boolean = false) {
        if (file == null || !file.exists() || file.length() == 0L) {
            release()
            return
        }
        if (loadedPath == file.absolutePath && mediaPlayer != null) {
            if (autoPlay && _state.value.isPrepared && !mediaPlayer!!.isPlaying) {
                togglePlayPause()
            }
            return
        }

        release()
        loadedPath = file.absolutePath
        _state.value = AudioPlaybackState(
            hasAudio = true,
            isPrepared = false,
            playbackSpeed = _state.value.playbackSpeed
        )

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    if (mediaPlayer !== mp) return@setOnPreparedListener
                    val dur = mp.duration.toLong().coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        hasAudio = true,
                        isPrepared = true,
                        durationMs = dur,
                        currentPositionMs = 0L,
                        errorMessage = null
                    )
                    if (autoPlay) {
                        applySpeedIfSupported(mp, _state.value.playbackSpeed)
                        if (runCatching { mp.start() }.isSuccess) {
                            _state.value = _state.value.copy(isPlaying = true)
                            startProgressTicker()
                        }
                    }
                }
                setOnCompletionListener { mp ->
                    if (mediaPlayer !== mp) return@setOnCompletionListener
                    progressJob?.cancel()
                    runCatching { mp.seekTo(0) }
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0L
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    progressJob?.cancel()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        errorMessage = appContext?.getString(R.string.err_audio_play) ?: "Unable to play audio clip."
                    )
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            _state.value = AudioPlaybackState(
                hasAudio = true,
                isPrepared = false,
                playbackSpeed = _state.value.playbackSpeed,
                errorMessage = e.localizedMessage ?: appContext?.getString(R.string.err_audio_load) ?: "Unable to load audio."
            )
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!_state.value.isPrepared) return

        if (player.isPlaying) {
            runCatching { player.pause() }
            progressJob?.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                currentPositionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            )
        } else {
            applySpeedIfSupported(player, _state.value.playbackSpeed)
            runCatching { player.start() }
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressTicker()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (!_state.value.isPrepared) return
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        runCatching { player.seekTo(clamped.toInt()) }
        _state.value = _state.value.copy(currentPositionMs = clamped)
    }

    fun seekToSeconds(seconds: Long, autoPlay: Boolean = true) {
        val player = mediaPlayer ?: return
        if (!_state.value.isPrepared) return
        val targetMs = (seconds * 1000L).coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        runCatching { player.seekTo(targetMs.toInt()) }
        _state.value = _state.value.copy(currentPositionMs = targetMs)
        if (autoPlay && !player.isPlaying) {
            applySpeedIfSupported(player, _state.value.playbackSpeed)
            runCatching { player.start() }
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressTicker()
        }
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - _state.value.playbackSpeed) < 0.05f }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        _state.value = _state.value.copy(playbackSpeed = nextSpeed)
        val player = mediaPlayer ?: return
        if (_state.value.isPrepared && player.isPlaying) {
            applySpeedIfSupported(player, nextSpeed)
        }
    }

    private fun applySpeedIfSupported(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params = player.playbackParams ?: PlaybackParams()
                player.playbackParams = params.setSpeed(speed)
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive && _state.value.isPlaying) {
                val player = mediaPlayer ?: break
                val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(_state.value.currentPositionMs)
                _state.value = _state.value.copy(currentPositionMs = pos)
                delay(150L)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        loadedPath = null
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
        _state.value = AudioPlaybackState(playbackSpeed = _state.value.playbackSpeed)
    }

    companion object {
        private const val TAG = "AudioPlayerManager"
    }
}

package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.export.NoteMarkdownExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ReadAloudState(
    val activeItemId: String? = null,
    val isSpeaking: Boolean = false,
    val isReady: Boolean = false
)

class ReadAloudManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var pendingSpeak: Pair<String, String>? = null

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private fun ensureInitialized() {
        if (tts != null) return
        runCatching {
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext) { status ->
                if (tts !== engine) {
                    runCatching { engine?.shutdown() }
                    return@TextToSpeech
                }
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        engine?.language = Locale.getDefault()
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _state.value = _state.value.copy(
                                    activeItemId = utteranceId,
                                    isSpeaking = true
                                )
                            }

                            override fun onDone(utteranceId: String?) {
                                if (_state.value.activeItemId == utteranceId) {
                                    _state.value = _state.value.copy(
                                        activeItemId = null,
                                        isSpeaking = false
                                    )
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (_state.value.activeItemId == utteranceId) {
                                    _state.value = _state.value.copy(
                                        activeItemId = null,
                                        isSpeaking = false
                                    )
                                }
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (_state.value.activeItemId == utteranceId) {
                                    _state.value = _state.value.copy(
                                        activeItemId = null,
                                        isSpeaking = false
                                    )
                                }
                            }
                        })
                    }
                    _state.value = _state.value.copy(isReady = true)
                    val queued = pendingSpeak
                    pendingSpeak = null
                    if (queued != null) {
                        speakInternal(queued.first, queued.second)
                    }
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status=$status")
                }
            }
            tts = engine
        }.onFailure { err ->
            Log.w(TAG, "Unable to create TextToSpeech engine", err)
        }
    }

    fun toggleSpeak(itemId: String, rawText: String) {
        val current = _state.value
        if (current.isSpeaking && current.activeItemId == itemId) {
            stop()
            return
        }

        val cleaned = NoteMarkdownExporter.stripMarkdown(rawText).trim()
        if (cleaned.isBlank()) return

        ensureInitialized()
        if (!_state.value.isReady) {
            pendingSpeak = itemId to cleaned
            _state.value = _state.value.copy(activeItemId = itemId, isSpeaking = true)
            return
        }
        speakInternal(itemId, cleaned)
    }

    private fun speakInternal(itemId: String, cleanedText: String) {
        val engine = tts ?: return
        runCatching {
            engine.stop()
            _state.value = _state.value.copy(activeItemId = itemId, isSpeaking = true)
            engine.speak(
                cleanedText.take(TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3800)),
                TextToSpeech.QUEUE_FLUSH,
                null,
                itemId
            )
        }.onFailure { err ->
            Log.w(TAG, "TextToSpeech speak failed", err)
            _state.value = _state.value.copy(activeItemId = null, isSpeaking = false)
        }
    }

    fun stop() {
        pendingSpeak = null
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(activeItemId = null, isSpeaking = false)
    }

    fun release() {
        pendingSpeak = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        _state.value = ReadAloudState()
    }

    companion object {
        private const val TAG = "ReadAloudManager"
    }
}

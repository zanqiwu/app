package com.opendroid.ai.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognitionEngine(context: Context) {

    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListening = false

    private companion object {
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }

    init {
        runOnMain { initializeRecognizer() }
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val language = if (Locale.getDefault().language == Locale.CHINESE.language) {
                    Locale.getDefault().toLanguageTag()
                } else {
                    Locale.SIMPLIFIED_CHINESE.toLanguageTag()
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Prolong listening limits to avoid early cut-offs
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
        }
    }

    private fun resetRecognizer() {
        isListening = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        recognizerIntent = null
        initializeRecognizer()
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onError: (String) -> Unit
    ) {
        runOnMain {
            startListeningOnMain(onResult, onPartialResult, onError)
        }
    }

    private fun startListeningOnMain(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isListening) {
            onError("Speech recognition engine busy")
            return
        }
        if (speechRecognizer == null) {
            initializeRecognizer()
            if (speechRecognizer == null) {
                onError("Speech recognition not available on this device")
                return
            }
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                val requestWasActive = isListening
                isListening = false
                if (!requestWasActive && error == SpeechRecognizer.ERROR_CLIENT) {
                    return
                }
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition engine busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timeout"
                    ERROR_TOO_MANY_REQUESTS -> "Speech recognition received too many requests"
                    ERROR_SERVER_DISCONNECTED -> "Speech recognition service disconnected"
                    ERROR_LANGUAGE_NOT_SUPPORTED -> "Speech recognition language not supported"
                    ERROR_LANGUAGE_UNAVAILABLE -> "Speech recognition language unavailable"
                    else -> "Unknown error ($error)"
                }
                onError(message)
                if (error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == ERROR_TOO_MANY_REQUESTS ||
                    error == ERROR_SERVER_DISCONNECTED
                ) {
                    // Xiaomi's recognition service can disconnect if it is destroyed
                    // from inside its own binder callback. Recreate it on the next loop.
                    mainHandler.post { resetRecognizer() }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                } else {
                    onError("No transcription results found")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onPartialResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            isListening = true
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            resetRecognizer()
            onError("Speech recognition failed to start: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun stopListening() {
        runOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
        }
    }

    fun cancelListening() {
        runOnMain {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            isListening = false
        }
    }

    fun destroy() {
        runOnMain {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            isListening = false
            speechRecognizer = null
            recognizerIntent = null
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

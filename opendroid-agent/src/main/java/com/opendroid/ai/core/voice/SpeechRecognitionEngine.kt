package com.opendroid.ai.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognitionEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    private companion object {
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onError: (String) -> Unit
    ) {
        if (speechRecognizer == null) {
            onError("Speech recognition not available on this device")
            return
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
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
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                } else {
                    onError("No transcription results found")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) onPartialResult(matches[0])
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            onError("Speech recognition failed to start: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        recognizerIntent = null
    }
}

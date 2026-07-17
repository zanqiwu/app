package com.example.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

object AudioSynthPlayer {
    private var activeJob: Job? = null
    private var isPlaying = false

    private val noteFrequencies = mapOf(
        "C4" to 261.63f, "D4" to 293.66f, "E4" to 329.63f, "F4" to 349.23f, "G4" to 392.00f, "A4" to 440.00f, "B4" to 493.88f,
        "C5" to 523.25f, "D5" to 587.33f, "E5" to 659.25f, "F5" to 698.46f, "G5" to 783.99f, "A5" to 880.00f, "B5" to 987.77f
    )

    fun startPlaying(
        notes: List<GeneratedNote>,
        onNotePlayed: (Int) -> Unit,
        onFinished: () -> Unit
    ) {
        stopPlaying()
        isPlaying = true
        activeJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 22050
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                @Suppress("DEPRECATION")
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize.coerceAtLeast(4096),
                    AudioTrack.MODE_STREAM
                )

                audioTrack.play()

                var noteIndex = 0
                while (isPlaying && noteIndex < notes.size) {
                    val note = notes[noteIndex]
                    val freq = noteFrequencies[note.pitch] ?: 440.0f
                    val durationMs = note.durationMs.coerceIn(150, 1500)
                    
                    withContext(Dispatchers.Main) {
                        onNotePlayed(noteIndex)
                    }

                    val numSamples = (sampleRate * (durationMs / 1000.0f)).toInt()
                    val sample = ShortArray(numSamples)
                    for (i in 0 until numSamples) {
                        val t = i.toDouble() / sampleRate
                        val angle = 2.0 * Math.PI * freq * t
                        // Create a warm organ/rhodes synth texture combining sine + minor triangle harmonic
                        val wave = 0.5 * sin(angle) + 0.25 * sin(angle * 2.0) + 0.1 * sin(angle * 3.0)
                        sample[i] = (wave * Short.MAX_VALUE).toInt().toShort()
                    }

                    audioTrack.write(sample, 0, numSamples)
                    delay(durationMs.toLong())
                    noteIndex++
                }

                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun stopPlaying() {
        isPlaying = false
        activeJob?.cancel()
        activeJob = null
    }
}

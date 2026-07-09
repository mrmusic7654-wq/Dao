package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.dao_engine.GeminiService
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class DaoTTS(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var isSpeaking = false
    private val cacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }

    suspend fun speak(text: String) {
        stop()

        // Check cache first
        val cacheKey = text.hashCode().toString()
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        
        if (cachedFile.exists()) {
            playAudio(cachedFile)
            return
        }

        // Generate TTS via Gemini
        try {
            val response = GeminiService.generateResponse(
                context,
                "Convert this text to speech instructions. Output only the text that should be spoken, with punctuation for pacing:\n\n$text",
                "TTS",
                "Direct"
            )

            // Use Gemini TTS model
            val ttsResponse = GeminiService.generateTTS(
                context,
                response.replyText,
                "gemini-2.5-flash-tts-preview"
            )

            if (ttsResponse != null) {
                // Save to cache
                FileOutputStream(cachedFile).use { it.write(ttsResponse) }
                playAudio(cachedFile)
            }
        } catch (e: Exception) {
            // Fall back to Android TTS
            android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    this@DaoTTS.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    private fun playAudio(file: File) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            setDataSource(file.absolutePath)
            prepare()
            start()
            isSpeaking = true
            setOnCompletionListener {
                isSpeaking = false
                release()
            }
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isSpeaking = false
    }

    fun isPlaying(): Boolean = isSpeaking
}

package com.example.data.dao_engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.*
import java.io.File

class LocalInferenceEngine(private val context: Context) {
    private var session: LlmInferenceSession? = null
    private var isModelLoaded = false
    private val modelFile: File
        get() = File(context.filesDir, "models/gemma-2b.bin")

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext true
        
        if (!modelFile.exists()) {
            return@withContext false // Model needs to be downloaded first
        }

        try {
            val options = LlmInferenceOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build())
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .setTopK(40)
                .build()

            session = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            true
        } catch (e: Exception) {
            isModelLoaded = false
            false
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            return@withContext "Local model not loaded. Connect to internet for cloud AI."
        }

        try {
            val result = session?.generateResponse(prompt) ?: ""
            result
        } catch (e: Exception) {
            "Local inference error: ${e.message}"
        }
    }

    fun downloadModel(onProgress: (Float) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://storage.googleapis.com/mediapipe-models/genai/gemma-2b/gemma-2b.bin")
                val connection = url.openConnection()
                val fileLength = connection.contentLength
                val input = connection.getInputStream()

                modelFile.parentFile?.mkdirs()
                val output = modelFile.outputStream()

                val buffer = ByteArray(4096)
                var totalRead = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileLength > 0) {
                        onProgress(totalRead.toFloat() / fileLength)
                    }
                }

                output.close()
                input.close()
                loadModel()
            } catch (e: Exception) {
                onProgress(-1f) // Error
            }
        }
    }

    fun isAvailable(): Boolean = isModelLoaded

    fun unload() {
        session?.close()
        session = null
        isModelLoaded = false
    }
}

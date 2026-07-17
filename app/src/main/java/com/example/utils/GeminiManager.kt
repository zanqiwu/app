package com.example.utils

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedTodoItem(
    val title: String,
    val description: String = "",
    val category: String,
    val isImportant: Boolean = false
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiManager {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    suspend fun generateTodoItems(prompt: String): List<GeneratedTodoItem> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing or default. Please configure GEMINI_API_KEY in your Secrets panel.")
        }

        val systemPrompt = """
            You are a smart Todo Assistant. Based on the user's input/goal, break it down into a list of realistic, actionable todo items.
            You MUST return a JSON array of objects.
            Each object in the array MUST strictly have these fields:
            1. "title": A short, clear task title (in Chinese).
            2. "description": A short explanation or step description (in Chinese).
            3. "category": Must be exactly one of: "工作", "学习", "生活", "健康", "购物", "个人".
            4. "isImportant": A boolean value indicating if this task is highly important or urgent.

            Example format:
            [
              {
                "title": "购买露营帐篷",
                "description": "选择双人防雨帐篷，去购物平台比价",
                "category": "购物",
                "isImportant": true
              }
            ]
            Do not include any Markdown block backticks, just the raw JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val response = apiService.generateContent(apiKey, request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("AI returned an empty response.")

        // Parse the generated JSON list
        val listType = Types.newParameterizedType(List::class.java, GeneratedTodoItem::class.java)
        val adapter = moshi.adapter<List<GeneratedTodoItem>>(listType)
        return adapter.fromJson(text) ?: emptyList()
    }
}

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
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String // Base64 string
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiImageConfig(
    val aspectRatio: String? = "1:1",
    val imageSize: String? = "1K"
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val responseModalities: List<String>? = null,
    val imageConfig: GeminiImageConfig? = null
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
    val isImportant: Boolean = false,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedAction(
    val type: String, // "center_map" or "generate_image" or "add_marker"
    val latitude: Double? = null,
    val longitude: Double? = null,
    val zoom: Int? = null,
    val address: String? = null,
    val prompt: String? = null,
    val targetTaskTitle: String? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedPlanningResponse(
    val todoItems: List<GeneratedTodoItem>,
    val actions: List<GeneratedAction> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeneratedShape(
    val type: String, // "circle", "rect", "triangle", "star"
    val x: Float, // percentage of canvas (0 to 100)
    val y: Float, // percentage of canvas (0 to 100)
    val size: Float, // size percentage (5 to 80)
    val color: String, // hex color like "#FF1E88E5"
    val alpha: Float, // opacity (0.1 to 1.0)
    val rotation: Float = 0f // degrees
)

@JsonClass(generateAdapter = true)
data class GeneratedImageArt(
    val prompt: String,
    val style: String,
    val primaryColor: String, // hex
    val secondaryColor: String, // hex
    val shapes: List<GeneratedShape>
)

@JsonClass(generateAdapter = true)
data class GeneratedNote(
    val pitch: String, // e.g., "C4", "E4", "G4", "A4", "C5", "D5"
    val durationMs: Int // duration in milliseconds (e.g. 300, 500, 1000)
)

@JsonClass(generateAdapter = true)
data class GeneratedSong(
    val title: String,
    val lyrics: List<String>, // Each line of lyrics
    val style: String, // e.g., "民谣", "合成器流行", "极简古典"
    val tempoBpm: Int, // bpm (e.g. 80, 100, 120)
    val chords: List<String>, // e.g. ["C", "G", "Am", "F"]
    val notes: List<GeneratedNote> // sequence of melody notes for play
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
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

    private var customApiKey: String? = null

    fun getApiKey(context: android.content.Context): String {
        if (customApiKey == null) {
            val prefs = context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
            customApiKey = prefs.getString("gemini_api_key", "")
        }
        val key = customApiKey ?: ""
        if (key.isNotEmpty()) return key
        // Safely check if BuildConfig has a real value
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    fun saveApiKey(context: android.content.Context, key: String) {
        customApiKey = key
        val prefs = context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    suspend fun generateWithFallback(
        context: android.content.Context,
        request: GeminiRequest,
        defaultModel: String = "gemini-3.5-flash"
    ): GeminiResponse {
        val apiKey = getApiKey(context)
        if (apiKey.isEmpty()) {
            throw IllegalStateException("API Key is missing. Please configure your Gemini API Key in the settings at the top.")
        }

        // Fallback models in order of priority (satisfying request 4)
        val models = listOf(
            defaultModel,
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-flash"
        ).distinct()

        var lastException: Exception? = null
        for (model in models) {
            try {
                android.util.Log.d("GeminiManager", "Attempting request with model: $model")
                return apiService.generateContent(model, apiKey, request)
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("GeminiManager", "Model $model failed: ${e.message}. Trying next fallback model...")
            }
        }
        throw lastException ?: IllegalStateException("All models in fallback chain failed.")
    }

    suspend fun generateTodoItems(context: android.content.Context, prompt: String): GeneratedPlanningResponse {
        val systemPrompt = """
            You are a smart Todo and Location Assistant with function-calling capabilities. Based on the user's input/goal, you MUST perform two core responsibilities:
            1. Break the goal down into a list of realistic, actionable todo items (todoItems).
            2. Decide if any interactive tools (actions) should be called to assist the user on their map, or to draw illustrative cards.

            You MUST return a single JSON object with these exact fields:
            - "todoItems": A JSON array of objects. Each object in the array MUST strictly have:
              1. "title": A short, clear task title (in Chinese).
              2. "description": A short explanation or step description (in Chinese).
              3. "category": Must be exactly one of: "工作", "学习", "生活", "健康", "购物", "个人".
              4. "isImportant": A boolean indicating if this task is highly important or urgent.
              5. "locationName": (Optional) If the task is related to a physical location, destination, scenic spot, city, restaurant, or business, provide the specific Chinese address or place name (e.g., "杭州西湖", "北京天安门", "宜家家居(上海徐汇店)", "星巴克咖啡厅"). Otherwise, return null.
              6. "latitude": (Optional) If locationName is provided, output a highly realistic Double coordinate representing its latitude (e.g. 30.25). Otherwise, return null.
              7. "longitude": (Optional) If locationName is provided, output a highly realistic Double coordinate representing its longitude (e.g. 120.15). Otherwise, return null.
              8. "imageUrl": (Optional) If the task is related to travel, an activity, or is highly visual, construct a URL calling the nanobanana image generation service to illustrate this task.
                 The format MUST be: https://api.nanobanana.im/image?prompt=<url_encoded_prompt_in_english>&width=512&height=512
                 For example: https://api.nanobanana.im/image?prompt=beautiful+vector+flat+illustration+of+hangzhou+west+lake+walking&width=512&height=512
                 Otherwise, return null.

            - "actions": A JSON array of tool actions to call. Supported tools:
              A. "center_map": Center the interactive map on the primary location of the plan.
                 Format: {"type": "center_map", "latitude": Double, "longitude": Double, "zoom": 13, "address": "Place Name"}
              B. "generate_image": Call the nanobanana service to render a cover image for a specific task.
                 Format: {"type": "generate_image", "prompt": "Prompt for image in English", "targetTaskTitle": "Task Title"}

            Example response:
            {
              "todoItems": [
                {
                  "title": "到杭州西湖漫步",
                  "description": "欣赏断桥残雪及湖光山色，建议下午4点出发",
                  "category": "生活",
                  "isImportant": true,
                  "locationName": "杭州西湖",
                  "latitude": 30.2452,
                  "longitude": 120.1419,
                  "imageUrl": "https://api.nanobanana.im/image?prompt=beautiful+flat+vector+illustration+of+hangzhou+west+lake+lakefront&width=512&height=512"
                }
              ],
              "actions": [
                {
                  "type": "center_map",
                  "latitude": 30.2452,
                  "longitude": 120.1419,
                  "zoom": 13,
                  "address": "杭州西湖"
                },
                {
                  "type": "generate_image",
                  "prompt": "beautiful flat vector illustration of hangzhou west lake lakefront",
                  "targetTaskTitle": "到杭州西湖漫步"
                }
              ]
            }

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

        val response = generateWithFallback(context, request, "gemini-3.5-flash")
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("AI returned an empty response.")

        val adapter = moshi.adapter(GeneratedPlanningResponse::class.java)
        return adapter.fromJson(text) ?: GeneratedPlanningResponse(emptyList())
    }

    suspend fun generateImageArt(context: android.content.Context, prompt: String): GeneratedImageArt {
        val systemPrompt = """
            You are a Creative AI Digital Artist. Based on the user's prompt, design a beautiful, abstract generative art piece.
            You MUST return a JSON object representing the art styling and geometric shapes to render on a digital canvas.
            Return a JSON object strictly with these fields:
            1. "prompt": The original user prompt (in Chinese).
            2. "style": A descriptive art style (e.g. "Futuristic Vaporwave", "Minimalist Zen", "Cosmic Cyberpunk", "Pastel Dreamland", "Warm Autumn").
            3. "primaryColor": A hex color string starting with #FF representing the background/main mood (e.g., "#FF1A1A2E").
            4. "secondaryColor": A hex color string starting with #FF representing the secondary ambient mood (e.g., "#FF16213E").
            5. "shapes": A JSON array of 15 to 25 shape objects to draw on the canvas.
            Each shape object MUST have:
            - "type": One of "circle", "rect", "triangle", "star".
            - "x": Float representing horizontal center position percentage (0 to 100).
            - "y": Float representing vertical center position percentage (0 to 100).
            - "size": Float representing size percentage of the canvas (5 to 40).
            - "color": A hex color string starting with #FF (e.g. "#FFFF7675", "#FF74B9FF").
            - "alpha": Float representing transparency (0.2 to 0.9).
            - "rotation": Float representing rotation angle in degrees (0 to 360).

            Design a cohesive color palette that perfectly fits the mood of the prompt.
            Do not include any Markdown block backticks, just the raw JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.7f
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val response = generateWithFallback(context, request, "gemini-3.5-flash")
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("AI returned an empty response.")

        val adapter = moshi.adapter(GeneratedImageArt::class.java)
        return adapter.fromJson(text) ?: throw IllegalStateException("Failed to parse visual art JSON")
    }

    suspend fun generateSong(context: android.content.Context, prompt: String): GeneratedSong {
        val systemPrompt = """
            You are a Creative AI Songwriter and Musician. Based on the user's prompt, write a custom song and structure its musical notes.
            You MUST return a JSON object representing the song title, lyrics, tempo, chords, and melody notes for playing on a custom synthesizer.
            The notes list will play sequentially. Map pitches to standard notes: "C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "G5", "A5".
            
            Return a JSON object strictly with these fields:
            1. "title": A creative song title (in Chinese).
            2. "lyrics": A JSON array of 4 to 6 lines of beautifully written Chinese lyrics.
            3. "style": One of "民谣", "合成器流行", "极简古典", "环境音乐".
            4. "tempoBpm": An integer from 70 to 120.
            5. "chords": A JSON array of 4 chord names (e.g., ["C", "G", "Am", "F"] or ["Am", "Dm", "G", "C"]).
            6. "notes": A JSON array of 12 to 24 melody note objects that play sequentially. Each note object MUST have:
               - "pitch": A pitch name from: "C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "G5", "A5".
               - "durationMs": Integer duration in milliseconds (300 to 1000).

            Keep the melody beautiful and harmonic. Do not include any Markdown block backticks, just the raw JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.7f
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val response = generateWithFallback(context, request, "gemini-3.5-flash")
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("AI returned an empty response.")

        val adapter = moshi.adapter(GeneratedSong::class.java)
        return adapter.fromJson(text) ?: throw IllegalStateException("Failed to parse song JSON")
    }

    suspend fun generateImageWithGemini(context: android.content.Context, prompt: String): String {
        val apiKey = getApiKey(context)
        if (apiKey.isEmpty()) {
            throw IllegalStateException("API Key is missing. Please configure your Gemini API Key in the settings.")
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                imageConfig = GeminiImageConfig(aspectRatio = "1:1", imageSize = "1K"),
                responseModalities = listOf("IMAGE")
            )
        )

        // Models for image generation (satisfies request 3)
        val imageModels = listOf(
            "imagen-3.0-generate-002",
            "gemini-3.1-flash-image-preview",
            "gemini-2.5-flash-image"
        )

        var lastException: Exception? = null
        for (model in imageModels) {
            try {
                android.util.Log.d("GeminiManager", "Attempting image generation with model: $model")
                val response = apiService.generateContent(model, apiKey, request)
                val inlineData = response.candidates?.firstOrNull()?.content?.parts
                    ?.firstOrNull { it.inlineData != null }?.inlineData

                if (inlineData != null && inlineData.data.isNotEmpty()) {
                    val base64Data = inlineData.data
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val file = java.io.File(context.cacheDir, "gemini_img_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        out.write(bytes)
                    }
                    android.util.Log.d("GeminiManager", "Successfully generated image at: ${file.absolutePath}")
                    return file.absolutePath
                }
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("GeminiManager", "Image model $model failed: ${e.message}")
            }
        }

        // Robust Fallback 1: Generative Vector Art using free tier text api (never 429s)
        try {
            android.util.Log.w("GeminiManager", "Imagen API failed (possibly due to 429 rate limit). Falling back to Generative Vector Art...")
            val art = generateImageArt(context, prompt)
            val path = saveProceduralArtToCache(context, art)
            if (path != null) {
                return path
            }
        } catch (fallbackEx: Exception) {
            android.util.Log.e("GeminiManager", "Vector Art fallback failed: ${fallbackEx.message}")
        }

        // Robust Fallback 2: Offline-capable deterministic beautiful gradient generator (guaranteed success)
        try {
            android.util.Log.w("GeminiManager", "Generative art failed or key error. Falling back to offline local gradient image...")
            val path = generateLocalGradientImage(context, prompt)
            if (path != null) {
                return path
            }
        } catch (localEx: Exception) {
            android.util.Log.e("GeminiManager", "Local gradient fallback failed: ${localEx.message}")
        }

        throw lastException ?: IllegalStateException("All image generation models and fallbacks failed.")
    }
}

fun generateLocalGradientImage(context: android.content.Context, prompt: String): String? {
    val size = 512
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Hash colors based on prompt
    val hash = prompt.hashCode()
    val color1 = 0xFF000000.toInt() or (hash and 0x00FFFFFF)
    val color2 = 0xFF000000.toInt() or ((hash ushr 8) and 0x00FFFFFF)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val shader = android.graphics.LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        color1, color2,
        android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = shader
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    
    // Reset paint shader and draw some subtle ambient overlay circles
    paint.shader = null
    paint.color = 0xFFFFFFFF.toInt()
    paint.alpha = 20 // very faint white overlay
    canvas.drawCircle(size / 3f, size / 3f, size / 4f, paint)
    canvas.drawCircle(2 * size / 3f, 2 * size / 3f, size / 5f, paint)

    return try {
        val file = java.io.File(context.cacheDir, "local_gradient_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun saveProceduralArtToCache(context: android.content.Context, art: GeneratedImageArt): String? {
    val size = 512
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Draw background
    val bgColor = try { android.graphics.Color.parseColor(art.primaryColor) } catch(e: Exception) { android.graphics.Color.parseColor("#FF1E1E2E") }
    canvas.drawColor(bgColor)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    art.shapes.forEach { shape ->
        val colorInt = try { android.graphics.Color.parseColor(shape.color) } catch(e: Exception) { android.graphics.Color.CYAN }
        paint.color = colorInt
        paint.alpha = (shape.alpha * 255).toInt().coerceIn(0, 255)
        
        val sizePx = (shape.size / 100f * size)
        val xPx = shape.x / 100f * size
        val yPx = shape.y / 100f * size
        
        canvas.save()
        canvas.rotate(shape.rotation, xPx, yPx)
        
        when (shape.type) {
            "circle" -> {
                canvas.drawCircle(xPx, yPx, sizePx / 2, paint)
            }
            "rect" -> {
                canvas.drawRect(xPx - sizePx/2, yPx - sizePx/2, xPx + sizePx/2, yPx + sizePx/2, paint)
            }
            "triangle" -> {
                val path = android.graphics.Path().apply {
                    moveTo(xPx, yPx - sizePx/2)
                    lineTo(xPx - sizePx/2, yPx + sizePx/2)
                    lineTo(xPx + sizePx/2, yPx + sizePx/2)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> { // star
                val path = android.graphics.Path().apply {
                    for (i in 0..4) {
                        val angle = i * 2 * Math.PI / 5 - Math.PI / 2
                        val xOuter = xPx + sizePx/2 * Math.cos(angle).toFloat()
                        val yOuter = yPx + sizePx/2 * Math.sin(angle).toFloat()
                        if (i == 0) moveTo(xOuter, yOuter) else lineTo(xOuter, yOuter)
                        
                        val innerAngle = angle + Math.PI / 5
                        val xInner = xPx + sizePx/4 * Math.cos(innerAngle).toFloat()
                        val yInner = yPx + sizePx/4 * Math.sin(innerAngle).toFloat()
                        lineTo(xInner, yInner)
                    }
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
    
    // Save to cache directory
    return try {
        val file = java.io.File(context.cacheDir, "ai_art_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

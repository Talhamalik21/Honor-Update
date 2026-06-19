package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(val text: String)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Candidate(val content: Content)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

class GeminiRepository {
    suspend fun analyzeDiagnostics(
        modelName: String,
        osVersion: String,
        cpuCores: Int,
        batteryPct: Int,
        batteryTemp: String,
        availableRamGb: String,
        availableStorageGb: String,
        thermalState: String,
        registeredIssues: List<String>
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Gemini API Key is not configured. Please add the GEMINI_API_KEY in the AI Studio Secrets Panel to get custom AI Diagnostic advice."
        }

        val prompt = """
            You are "MagicOS Care Assistant", a software diagnostics agent for Honor smartphones.
            Analyze the following device telemetry information and provide a helpful, concise response:
            - Device Engine: Honor MagicOS Companion Utility
            - Active OS Version: $osVersion
            - Processor Configuration: $cpuCores cores
            - Battery Level: $batteryPct% (Temp: $batteryTemp)
            - Free Storage Space: $availableStorageGb
            - Free RAM: $availableRamGb
            - Thermal State: $thermalState
            - User Reported/Sensor Issues: ${if (registeredIssues.isEmpty()) "None detected" else registeredIssues.joinToString(", ")}

            Please output your diagnostics evaluation in short markdown:
            1. **Overall Health Status** (Excellent, Good, Decent, or Action Needed)
            2. **Key Findings**: Max 2 bullet points about constraints or hardware metrics.
            3. **MagicOS Pro-Tips**: Custom recommendations specifically for Honor's MagicOS system configurations (like scheduled optimization, cleaning battery-hogging applications via RAM cleaner, or system partition cleanup) to improve device responsiveness.

            Keep the tone professional, direct, elegant, and reassuring. Avoid long introductions. Avoid repeating raw values unnecessarily.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Received empty response from MagicOS Care Diagnostics service."
        } catch (e: Exception) {
            "Failed to communicate with diagnostic service: ${e.localizedMessage}. Please verify your network and AI Studio API keys."
        }
    }

    suspend fun analyzeUpdateCompatibility(
        versionName: String,
        changelog: String,
        batteryPct: Int,
        freeStorageGb: String,
        ramInfo: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Gemini API Key is not configured. Please add the GEMINI_API_KEY in the AI Studio Secrets Panel to get custom AI Firmware analysis."
        }

        val prompt = """
            You are "MagicOS System Architect".
            Provide a smart, concise AI Software Update impact report about this firmware version:
            - Target Update: $versionName
            - Key Features: $changelog
            
            Current Device Context:
            - Battery Level: $batteryPct%
            - Free Storage: $freeStorageGb
            - Memory Status: $ramInfo
            
            Evaluate using this exact short markdown format:
            1. **Update Impact Forecast**: Highlight how the key features of $versionName will affect performance on their system (e.g., will RAM be optimized? will any system lag decrease?).
            2. **Prerequisite Check**: Confirm they meet current storage and battery requisites to install safely.
            3. **Recommendation Grade**: Give a visual grading (e.g., [RECOMMENDED / CRITICAL] or [RECOMMENDED / STANDARD] or [BATTERY LOW - CHARGE FIRST]).
            
            Keep the tone clean, minimal, deeply technical but readable, reassuring and brief.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Empty response from firmware compatibility service."
        } catch (e: Exception) {
            "Could not reach compatibility advisor: ${e.localizedMessage}"
        }
    }
}

package com.cleantv.launcher.data.jarvis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(@Path("model") model: String, @Body body: JsonObject): JsonObject

    companion object {
        fun create(key: String): GeminiApi {
            val http = OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("x-goog-api-key", key).build()) }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(http)
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GeminiApi::class.java)
        }
    }
}

/**
 * Free-tier Gemini calls. Models are tried in order: the lite one answers in ~2 s, and on the free tier a busy
 * model returns 503, so the next one is tried rather than failing. (Names checked against this key's model
 * list on 2026-11-03; the "-latest" aliases follow Google's current releases.)
 */
class Gemini(private val api: GeminiApi) {
    suspend fun generate(body: JsonObject): JsonObject {
        var last: Exception? = null
        for (model in MODELS) {
            try {
                return api.generate(model, body)
            } catch (e: HttpException) {
                // 503 busy, 429 over the free quota for this model, 404 retired: all worth trying the next one.
                if (e.code() !in setOf(404, 429, 500, 503)) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("No Gemini model available")
    }

    private companion object {
        val MODELS = listOf("gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest")
    }
}

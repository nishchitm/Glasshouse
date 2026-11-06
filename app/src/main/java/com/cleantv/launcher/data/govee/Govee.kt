package com.cleantv.launcher.data.govee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One controllable Govee light. */
data class GoveeLight(val sku: String, val device: String, val name: String)

/**
 * Govee's cloud API (https://developer.govee.com), with the key from local.properties: the lights, and the
 * "DreamView" scenes saved in the Govee app.
 */
class Govee(private val key: String) {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var devices: List<Pair<String?, GoveeLight>>? = null // (type, device)

    /** The lights (bulbs, strips, the TV backlight). */
    suspend fun lights(): List<GoveeLight> = devices().filter { it.first == "devices.types.light" }.map { it.second }

    /** Scenes saved in the Govee app ("DreamView" setups, e.g. "Evening setup"); switching one on runs it. */
    suspend fun scenes(): List<GoveeLight> = devices().filter { it.second.sku == "DreamViewScenic" }.map { it.second }

    private suspend fun devices(): List<Pair<String?, GoveeLight>> {
        mutex.withLock { devices }?.let { return it }
        val body = call("GET", "user/devices", null)
        val found = body["data"]?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            o["type"]?.jsonPrimitive?.content to GoveeLight(
                o["sku"]!!.jsonPrimitive.content,
                o["device"]!!.jsonPrimitive.content,
                o["deviceName"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
        return found.also { mutex.withLock { devices = it } }
    }

    suspend fun power(light: GoveeLight, on: Boolean) =
        control(light, "devices.capabilities.on_off", "powerSwitch", JsonPrimitive(if (on) 1 else 0))

    suspend fun color(light: GoveeLight, rgb: Int) =
        control(light, "devices.capabilities.color_setting", "colorRgb", JsonPrimitive(rgb and 0xFFFFFF))

    suspend fun colorTemperature(light: GoveeLight, kelvin: Int) =
        control(light, "devices.capabilities.color_setting", "colorTemperatureK", JsonPrimitive(kelvin))

    suspend fun brightness(light: GoveeLight, percent: Int) =
        control(light, "devices.capabilities.range", "brightness", JsonPrimitive(percent.coerceIn(1, 100)))

    private suspend fun control(light: GoveeLight, type: String, instance: String, value: JsonElement) {
        val body = buildJsonObject {
            put("requestId", UUID.randomUUID().toString())
            putJsonObject("payload") {
                put("sku", light.sku)
                put("device", light.device)
                putJsonObject("capability") {
                    put("type", type)
                    put("instance", instance)
                    put("value", value)
                }
            }
        }
        call("POST", "device/control", body.toString())
    }

    /** Govee can refuse with HTTP 200 and an error code in the body, and limits bursts: one retry after a pause. */
    private suspend fun call(method: String, path: String, body: String?): JsonObject {
        val first = send(method, path, body)
        return if (first.first == 429) {
            delay(2_000)
            send(method, path, body).also { check(it.first != 429) { "Govee $path: rate limited" } }.second
        } else first.second
    }

    private suspend fun send(method: String, path: String, body: String?) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://openapi.api.govee.com/router/api/v1/$path")
            .header("Govee-API-Key", key)
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { r ->
            if (r.code == 429) return@withContext 429 to JsonObject(emptyMap())
            check(r.isSuccessful) { "Govee $path: HTTP ${r.code}" }
            val parsed = json.parseToJsonElement(r.body!!.string()).jsonObject
            val code = parsed["code"]?.jsonPrimitive?.intOrNull
            if (code == 429) return@withContext 429 to parsed
            check(code == null || code == 200) { "Govee $path: code $code ${parsed["message"] ?: parsed["msg"]}" }
            r.code to parsed
        }
    }
}

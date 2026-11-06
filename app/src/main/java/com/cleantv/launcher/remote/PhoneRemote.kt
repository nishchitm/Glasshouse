package com.cleantv.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.core.graphics.drawable.toBitmap
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.MainActivity
import com.cleantv.launcher.data.govee.Lights
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The phone remote: a page on the home Wi-Fi (port 8081) to type to Jarvis, open apps, go Home/Back, change
 * volume, lights and the sleep timer. Every call needs the 4-digit PIN shown in Quick settings.
 */
class PhoneRemote(private val app: CleanTvApplication) : NanoHTTPD(PORT) {
    private val prefs = app.getSharedPreferences("phone_remote", Context.MODE_PRIVATE)
    val pin: String = prefs.getString("pin", null) ?: "%04d".format(Random.nextInt(10_000)).also {
        prefs.edit().putString("pin", it).apply()
    }

    private val icons = ConcurrentHashMap<String, ByteArray>()

    override fun serve(session: IHTTPSession): Response = try {
        if (session.method == Method.POST) session.parseBody(mutableMapOf())
        val p = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        when {
            session.uri == "/" -> page()
            p["pin"] != pin -> json(buildJsonObject { put("ok", false); put("error", "pin") }, Response.Status.FORBIDDEN)
            session.uri == "/icon" -> icon(p["pkg"].orEmpty())
            else -> json(handle(session.uri, p))
        }
    } catch (e: Exception) {
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
    }

    private fun handle(uri: String, p: Map<String, String>): JsonObject {
        when (uri) {
            "/api/check" -> Unit
            "/api/ask" -> toHome(Intent().putExtra(MainActivity.EXTRA_ASK, p["text"].orEmpty()))
            "/api/search" -> toHome(Intent().putExtra(MainActivity.EXTRA_SEARCH, p["text"].orEmpty()))
            "/api/open" -> app.launcher.launchPackage(p["pkg"].orEmpty())
            "/api/home" -> RemoteKeyService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                ?: toHome(Intent())
            "/api/back" -> RemoteKeyService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "/api/volume" -> {
                val audio = app.getSystemService(AudioManager::class.java)
                val dir = when (p["dir"]) {
                    "up" -> AudioManager.ADJUST_RAISE
                    "down" -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            }
            "/api/lights" -> when (val preset = p["preset"].orEmpty()) {
                in Lights.Preset.entries.map { it.name } -> app.lights.apply(Lights.Preset.valueOf(preset))
                else -> app.lights.scene(preset)
            }
            "/api/sleep" -> app.nightMode.setSleepTimer(p["minutes"]?.toIntOrNull() ?: 0)
            "/api/apps" -> return buildJsonObject {
                put("ok", true)
                putJsonArray("scenes") { runBlocking { app.lights.sceneNames() }.forEach { add(it) } }
                putJsonArray("apps") {
                    app.launcher.appNames().sortedBy { it.second.lowercase() }.forEach { (pkg, name) ->
                        addJsonObject { put("pkg", pkg); put("name", name) }
                    }
                }
            }
            else -> return buildJsonObject { put("ok", false); put("error", "unknown") }
        }
        return buildJsonObject { put("ok", true) }
    }

    private fun toHome(extras: Intent) {
        app.startActivity(Intent(app, MainActivity::class.java).putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** An app's TV banner (or icon) as a PNG, for the page's app grid. */
    private fun icon(pkg: String): Response {
        val pm = app.packageManager
        val png = icons.getOrPut(pkg) {
            val drawable = pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg)
            ByteArrayOutputStream().also { drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        return newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(png), png.size.toLong())
            .apply { addHeader("Cache-Control", "max-age=86400") }
    }

    private fun page(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", app.assets.open("remote/index.html").bufferedReader().use { it.readText() })
            .apply { addHeader("Cache-Control", "no-store") }

    private fun json(body: JsonObject, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json", body.toString()).apply { addHeader("Cache-Control", "no-store") }

    companion object {
        const val PORT = 8081
    }
}

package com.cleantv.launcher.data.govee

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Room-light presets over all Govee lights; used by Quick settings, party mode, night mode and Jarvis.
 * All lights change together, the newest press wins, and a light that didn't respond is reported through [notify].
 */
class Lights(private val govee: Govee?, private val scope: CoroutineScope, private val notify: (String) -> Unit = {}) {
    val available get() = govee != null
    private var running: Job? = null

    enum class Preset(val label: String) { On("All on"), Off("All off"), Movie("Movie"), Bright("Bright"), Gold("Festive gold") }

    fun apply(preset: Preset) = eachLight { govee, light ->
        when (preset) {
            Preset.Off -> govee.power(light, false)
            Preset.On -> govee.power(light, true)
            // Soft glow behind the picture; the rest of the room barely on.
            Preset.Movie -> if (light.isTvBacklight) set(govee, light, 20, kelvin = 2700) else set(govee, light, 5, kelvin = 2200)
            Preset.Bright -> set(govee, light, 100, kelvin = 4000)
            Preset.Gold -> set(govee, light, 80, rgb = 0xFFA020)
        }
    }

    /** Soft warm white, the "normal" state party mode returns to. */
    fun warmWhite() = eachLight { govee, light -> set(govee, light, 60, kelvin = 2700) }

    /** Runs a scene saved in the Govee app, by name. */
    fun scene(name: String) {
        val govee = govee ?: return
        running?.cancel()
        running = scope.launch {
            runCatching { govee.scenes().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { govee.power(it, true) } }
                .onFailure {
                    Log.w(TAG, "scene $name: $it")
                    notify("Lights: couldn't run $name")
                }
        }
    }

    suspend fun sceneNames(): List<String> = runCatching { govee?.scenes()?.map { it.name } }.getOrNull().orEmpty()

    private suspend fun set(govee: Govee, light: GoveeLight, brightness: Int, kelvin: Int? = null, rgb: Int? = null) {
        govee.power(light, true)
        kelvin?.let { govee.colorTemperature(light, it) }
        rgb?.let { govee.color(light, it) }
        govee.brightness(light, brightness)
    }

    private fun eachLight(block: suspend (Govee, GoveeLight) -> Unit) {
        val govee = govee ?: return
        running?.cancel()
        running = scope.launch {
            val lights = runCatching { govee.lights() }.getOrElse {
                Log.w(TAG, "lights: $it")
                notify("Lights: couldn't reach Govee")
                return@launch
            }
            val failed = lights.map { light ->
                async {
                    runCatching { block(govee, light) }
                        .onFailure { Log.w(TAG, "${light.name}: $it") }
                        .exceptionOrNull()?.let { light.name }
                }
            }.awaitAll().filterNotNull()
            if (failed.isNotEmpty()) notify("Lights: ${failed.joinToString()} didn't respond")
        }
    }

    private val GoveeLight.isTvBacklight get() = name.contains("TV", ignoreCase = true)

    private companion object {
        const val TAG = "Lights"
    }
}

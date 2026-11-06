package com.cleantv.launcher.party

import com.cleantv.launcher.data.govee.Lights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Party mode: the Diwali wallpaper on the home screen and every Govee light in warm festive gold. Turning it
 * off sets the lights back to a soft warm white.
 */
class PartyMode(private val lights: Lights) {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    fun set(on: Boolean) {
        _on.value = on
        if (on) lights.apply(Lights.Preset.Gold) else lights.warmWhite()
    }
}

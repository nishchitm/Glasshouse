package com.cleantv.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide switches, kept across restarts. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Hide titles that don't stream on the user's services (For You, Trending, Discover, Jarvis). */
    private val _onlyMine = MutableStateFlow(prefs.getBoolean(KEY_ONLY_MINE, true))
    val onlyMine: StateFlow<Boolean> = _onlyMine

    fun setOnlyMine(on: Boolean) {
        _onlyMine.value = on
        prefs.edit().putBoolean(KEY_ONLY_MINE, on).apply()
    }

    private companion object {
        const val KEY_ONLY_MINE = "only_mine"
    }
}

package com.cleantv.launcher.data

import android.content.Context
import com.cleantv.launcher.data.tmdb.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Titles the viewer never wants to see on the home screen's rows. Search still finds them. */
class HiddenTitles(context: Context) {
    private val prefs = context.getSharedPreferences("hidden", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty())
    val keys: StateFlow<Set<String>> = _keys

    fun hide(media: Media) = save(_keys.value + media.key)

    fun clear() = save(emptySet())

    private fun save(keys: Set<String>) {
        _keys.value = keys
        prefs.edit().putStringSet(KEY, keys).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}

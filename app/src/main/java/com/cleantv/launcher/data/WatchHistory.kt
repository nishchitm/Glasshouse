package com.cleantv.launcher.data

import android.content.Context
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A title opened from Glasshouse or played in an app, and on which service. */
data class Watched(val media: Media, val service: StreamingService, val at: Long, val link: String? = null)

/**
 * "Continue watching". Android doesn't let a launcher read the streaming apps' own progress, so this is
 * Glasshouse's own record: every title opened from here or seen playing by MediaWatcher, newest first, kept for 30 days.
 */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Watched>> = _items

    /** @param link the title's own page in the app, when known; a link found earlier is kept. */
    fun opened(media: Media, service: StreamingService, link: String? = null) {
        val known = link ?: _items.value.firstOrNull { it.media.key == media.key }?.link
        save((listOf(Watched(media, service, System.currentTimeMillis(), known)) + _items.value.filter { it.media.key != media.key }).take(MAX))
    }

    fun remove(media: Media) {
        prefs.edit().remove(POSITION + media.key).apply()
        save(_items.value.filter { it.media.key != media.key })
    }

    /** How far into [media] the viewer got. Kept apart from [items], so it doesn't refresh the home screen as it plays. */
    fun savePosition(media: Media, ms: Long) = prefs.edit().putLong(POSITION + media.key, ms).apply()

    fun position(media: Media) = prefs.getLong(POSITION + media.key, 0)

    private fun save(list: List<Watched>) {
        _items.value = list
        prefs.edit().putString(KEY, json.encodeToString(list.map { Entry.of(it) })).apply()
    }

    private fun load(): List<Watched> {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        return runCatching { json.decodeFromString<List<Entry>>(prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())
            .mapNotNull { it.toWatched() }
            .filter { it.at > cutoff }
    }

    @Serializable
    private data class Entry(
        val id: Int,
        val type: String,
        val title: String,
        val overview: String = "",
        val year: String? = null,
        val rating: Double = 0.0,
        val backdropUrl: String,
        val genreIds: List<Int> = emptyList(),
        val service: String,
        val at: Long,
        val link: String? = null,
    ) {
        fun toWatched(): Watched? {
            val service = StreamingService.entries.firstOrNull { it.name == service } ?: return null
            val type = MediaType.entries.firstOrNull { it.path == type } ?: return null
            return Watched(Media(id, type, title, overview, year, rating, backdropUrl, genreIds), service, at, link)
        }

        companion object {
            fun of(w: Watched) = Entry(
                w.media.id, w.media.type.path, w.media.title, w.media.overview, w.media.year, w.media.rating,
                w.media.backdropUrl, w.media.genreIds, w.service.name, w.at, w.link,
            )
        }
    }

    private companion object {
        const val KEY = "watched"
        const val POSITION = "position:"
        const val MAX = 20
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}

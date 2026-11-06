package com.cleantv.launcher.remote

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Fills Continue watching with what's played in the streaming apps, however they were opened. Android only shows
 * "now playing" to a notification listener, so that's what this is; it never looks at the notifications themselves.
 *
 * A title counts after [MIN_WATCH_MS] of play, so autoplaying previews stay out. Apps that don't say what's playing
 * get a card for the app itself.
 */
class MediaWatcher : NotificationListenerService() {
    private val graph get() = application as CleanTvApplication
    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private val me by lazy { ComponentName(this, MediaWatcher::class.java) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var pending: Job? = null
    private var pendingKey: String? = null
    private var ticker: Job? = null
    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    override fun onListenerConnected() {
        sessions.addOnActiveSessionsChangedListener(onSessions, me)
        attach(sessions.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        sessions.removeOnActiveSessionsChangedListener(onSessions)
        attach(emptyList())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun attach(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        (watched.keys - tokens).forEach { watched.remove(it)?.let { (c, cb) -> c.unregisterCallback(cb) } }
        controllers.filter { it.sessionToken !in watched }.forEach { c ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = check(c)
                override fun onMetadataChanged(metadata: MediaMetadata?) = check(c)
            }
            c.registerCallback(callback)
            watched[c.sessionToken] = c to callback
            check(c)
        }
    }

    /** While anything plays: saves how far it got, and takes a finished clip or film off the row. */
    private fun track() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                val playing = watched.values.map { it.first }.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                if (playing.isEmpty()) break
                playing.forEach { c -> runCatching { progress(c) }.onFailure { Log.w(TAG, "progress: $it") } }
            }
        }
    }

    private fun progress(c: MediaController) {
        val service = StreamingService.entries.firstOrNull { c.packageName in it.packages } ?: return
        val title = c.metadata?.title() ?: return
        val position = c.position() ?: return
        val media = graph.history.items.value.firstOrNull { it.service == service && it.media.title.same(title) }?.media ?: return
        // A show stays when an episode ends: there's a next one.
        if (media.type == MediaType.Tv && media.id > 0) return
        if (finished(service, position, c.duration())) {
            graph.history.remove(media)
        } else {
            graph.history.savePosition(media, position)
        }
    }

    private fun MediaController.position(): Long? {
        val state = playbackState ?: return null
        if (state.position < 0) return null
        return state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
    }

    private fun MediaController.duration() = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    /** Close enough to the end to count as watched. Films run credits for longer than a clip's end screen is worth waiting for. */
    private fun finished(service: StreamingService, position: Long, duration: Long) =
        duration > 0 && position >= duration * (if (service == StreamingService.YouTube) 0.9 else 0.95)

    private fun check(c: MediaController) {
        val service = StreamingService.entries.firstOrNull { c.packageName in it.packages }
            ?.takeIf { it != StreamingService.Spotify } ?: return
        if (c.playbackState?.state != PlaybackState.STATE_PLAYING) {
            if (pendingKey?.startsWith(c.packageName) == true) {
                pending?.cancel()
                pendingKey = null
            }
            return
        }
        track()
        val meta = c.metadata
        val key = "${c.packageName}|${meta?.title().orEmpty()}"
        if (key == pendingKey) return
        pending?.cancel()
        pendingKey = key
        pending = scope.launch {
            delay(MIN_WATCH_MS)
            // Already at the end, e.g. resumed for the last few seconds: nothing left to continue.
            if (!finished(service, c.position() ?: 0, c.duration())) runCatching { record(service, meta) }.onFailure { Log.w(TAG, "record: $it") }
        }
    }

    private suspend fun record(service: StreamingService, meta: MediaMetadata?) {
        val title = meta?.title()
        val context = meta?.context().orEmpty()
        val history = graph.history
        val media = when {
            title == null -> Media(-(service.ordinal + 1), MediaType.Tv, service.label, "", null, 0.0, "")
            else -> history.items.value.firstOrNull { it.service == service && it.media.title.same(title) }?.media
                ?: tmdb(service, title, context)
                ?: Media(-(("$service|$title").hashCode().absoluteValue + 100), MediaType.Tv, title, context.firstOrNull().orEmpty(), null, 0.0, art(meta, title))
        }
        // Looked up once, while the title is playing, so the card opens straight away later.
        val known = history.items.value.firstOrNull { it.media.key == media.key }?.link
        val link = if (service == StreamingService.YouTube && title != null && known == null) graph.youtube.link(title, context.firstOrNull().orEmpty()) else null
        history.opened(media, service, link)
    }

    /** The show or film on TMDB, only on an exact name match: a wrong poster is worse than none. YouTube videos aren't there. */
    private suspend fun tmdb(service: StreamingService, title: String, context: List<String>): Media? {
        if (service == StreamingService.YouTube) return null
        val repo = graph.repo ?: return null
        // Episodes usually give the show as album or artist; films only have a title.
        val tries = context.map { it to MediaType.Tv } + listOf(title to MediaType.Movie, title to MediaType.Tv)
        return withContext(Dispatchers.IO) {
            tries.firstNotNullOfOrNull { (name, type) ->
                runCatching { repo.findTitle(name, null, type) }.getOrNull()?.takeIf { it.title.same(name) }
            }
        }
    }

    /** The app's own artwork for this title, kept in a file, or "" if it gives none. */
    private suspend fun art(meta: MediaMetadata?, title: String): String = withContext(Dispatchers.IO) {
        meta ?: return@withContext ""
        val bitmap = listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .firstNotNullOfOrNull { meta.getBitmap(it) }
        if (bitmap != null) {
            val file = File(File(filesDir, "continue").apply { mkdirs() }, "${title.hashCode().absoluteValue}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return@withContext "file://${file.absolutePath}"
        }
        listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { k -> meta.getString(k)?.takeIf { it.startsWith("http") } }.orEmpty()
    }

    private fun MediaMetadata.title() =
        (getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.takeIf { it.isNotBlank() }

    private fun MediaMetadata.context() = listOf(
        MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
        MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
    ).mapNotNull { getString(it)?.takeIf { s -> s.isNotBlank() } }.distinct()

    private fun String.same(other: String) = StreamingService.normalise(this) == StreamingService.normalise(other)

    private companion object {
        const val TAG = "MediaWatcher"
        const val MIN_WATCH_MS = 60_000L
        const val TICK_MS = 5_000L
    }
}

package com.cleantv.launcher.party

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cleantv.launcher.BuildConfig
import com.cleantv.launcher.MainActivity
import com.cleantv.launcher.remote.MediaWatcher

/**
 * Party music, played by the TV's own Spotify or YouTube Music apps: no logins or keys here. A Spotify playlist link
 * starts playing by itself, and the app carries on behind Glasshouse; the buttons work through Android's media controls,
 * which need the same access Continue watching uses.
 */
class PartyMusic(private val context: Context) {
    data class Playlist(val label: String, val uri: String)
    data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val art: Bitmap? = null)

    /** From the `party.playlists` setting: "Name|spotify:playlist:id;Name|spotify:playlist:id". */
    val playlists = BuildConfig.PARTY_PLAYLISTS.split(';').mapNotNull { entry ->
        val parts = entry.split('|')
        parts.getOrNull(1)?.let { Playlist(parts[0].trim(), it.trim()) }
    }
    private var pausedForHumming = false

    /** Starts [playlist] on Spotify, then comes back to the game once it has had time to start. */
    fun play(playlist: Playlist): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playlist.uri)).setPackage(SPOTIFY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) == null) return false
        context.startActivity(intent)
        Handler(Looper.getMainLooper()).postDelayed({
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, BACK_AFTER_MS)
        return true
    }

    /** YouTube Music can't be told what to play from outside, so this opens it for picking by hand. */
    fun openYouTubeMusic(): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(YOUTUBE_MUSIC) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun now(): NowPlaying? = player()?.let { c ->
        val meta = c.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.description?.title?.toString() ?: return null
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        NowPlaying(title, meta.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(), c.isPlaying(), art)
    }

    fun togglePlay() {
        val c = player() ?: return
        if (c.isPlaying()) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = player()?.transportControls?.skipToNext() ?: Unit

    fun previous() = player()?.transportControls?.skipToPrevious() ?: Unit

    /** Hum It's hummer shouldn't compete with the real song. Only resumes what this paused. */
    fun holdForHumming(hold: Boolean) {
        val c = player() ?: return
        if (hold && c.isPlaying()) {
            pausedForHumming = true
            c.transportControls.pause()
        } else if (!hold && pausedForHumming) {
            pausedForHumming = false
            c.transportControls.play()
        }
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    /** Whichever music app is playing, else the one used last. */
    private fun player(): MediaController? = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaWatcher::class.java))
            .filter { it.packageName in MUSIC_APPS }
            .let { sessions -> sessions.firstOrNull { it.isPlaying() } ?: sessions.firstOrNull() }
    }.getOrNull()

    private companion object {
        const val SPOTIFY = "com.spotify.tv.android"
        const val YOUTUBE_MUSIC = "com.google.android.youtube.tvmusic"
        val MUSIC_APPS = setOf(SPOTIFY, YOUTUBE_MUSIC, "com.google.android.youtube.tv")
        const val BACK_AFTER_MS = 5_000L
    }
}

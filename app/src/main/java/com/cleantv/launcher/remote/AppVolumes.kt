package com.cleantv.launcher.remote

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

/**
 * Remembers the volume used in each app and restores it on switching back (F1 TV louder, YouTube quieter…).
 * Learned, not configured: when you leave an app, its volume is saved.
 */
class AppVolumes(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("app_volumes", Context.MODE_PRIVATE)
    private var current: String? = null
    private val apps: Set<String> by lazy {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.toSet() - context.packageName
    }

    /** A window came to the front. Only real apps count: not the keyboard, system UI or Glasshouse itself. */
    fun onForeground(pkg: String) {
        if (pkg == current || pkg !in apps) return
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        current?.let { prefs.edit().putInt(it, volume).apply() }
        current = pkg
        val saved = prefs.getInt(pkg, -1)
        if (saved >= 0 && saved != volume) {
            Log.i(TAG, "$pkg: volume $volume → $saved")
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
        }
    }

    /** Back to the home screen: remember the volume of the app just left. */
    fun onHome() {
        current?.let { prefs.edit().putInt(it, audio.getStreamVolume(AudioManager.STREAM_MUSIC)).apply() }
        current = null
    }

    private companion object {
        const val TAG = "AppVolumes"
    }
}

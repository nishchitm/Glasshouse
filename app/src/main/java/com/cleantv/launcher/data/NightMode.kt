package com.cleantv.launcher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cleantv.launcher.data.weather.HomeLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

/**
 * Night mode (23:00–06:00 local time, on by default): the home screen dims and Jarvis speaks quietly.
 * Also the sleep timer, which turns the screen off after 30/60/90 minutes, even from inside another app.
 */
class NightMode(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_NIGHT, true))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_NIGHT, on).apply()
    }

    fun isNight(now: LocalTime = LocalTime.now(HomeLocation.zone)) =
        _enabled.value && (now.hour >= 23 || now.hour < 6)

    // ---------- sleep timer

    private val _sleepAt = MutableStateFlow(prefs.getLong(KEY_SLEEP_AT, 0L).takeIf { it > System.currentTimeMillis() })
    /** When the screen will turn off (epoch ms), or null. */
    val sleepAt: StateFlow<Long?> = _sleepAt

    /** Off → 30 → 60 → 90 min → off. */
    fun cycleSleepTimer() {
        val remainingMin = _sleepAt.value?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() } ?: 0
        val next = SLEEP_STEPS.firstOrNull { it > remainingMin + 1 } ?: 0
        setSleepTimer(next)
    }

    fun setSleepTimer(minutes: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(sleepIntent())
        if (minutes <= 0) {
            _sleepAt.value = null
            prefs.edit().remove(KEY_SLEEP_AT).apply()
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        // Inexact is fine for a sleep timer, and needs no special alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleepIntent())
        _sleepAt.value = at
        prefs.edit().putLong(KEY_SLEEP_AT, at).apply()
    }

    internal fun fired() {
        _sleepAt.value = null
        prefs.edit().remove(KEY_SLEEP_AT).apply()
    }

    private fun sleepIntent() = PendingIntent.getBroadcast(
        context, 0, Intent(context, SleepReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val KEY_NIGHT = "night_mode"
        const val KEY_SLEEP_AT = "sleep_at"
        val SLEEP_STEPS = listOf(30, 60, 90)
    }
}

/** The sleep timer went off: turn the screen off, as Quick settings' "Screen off" does. */
class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as com.cleantv.launcher.CleanTvApplication).nightMode.fired()
        runCatching {
            context.startActivity(Intent("com.google.android.pano.action.SLEEP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

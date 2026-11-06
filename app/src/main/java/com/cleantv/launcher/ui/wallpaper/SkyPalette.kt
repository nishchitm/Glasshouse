package com.cleantv.launcher.ui.wallpaper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import java.time.LocalTime

/** Colours for every wallpaper at one moment of the day. [night] runs 0 (full day) to 1 (full night). */
data class SkyPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val accents: List<Color>,
    val glow: Color,
    val night: Float,
) {
    fun lerpTo(other: SkyPalette, f: Float) = SkyPalette(
        lerp(skyTop, other.skyTop, f),
        lerp(skyBottom, other.skyBottom, f),
        accents.zip(other.accents) { a, b -> lerp(a, b, f) },
        lerp(glow, other.glow, f),
        lerp(night, other.night, f),
    )
}

private val Night = SkyPalette(
    Color(0xFF060A1C), Color(0xFF1A1E48),
    listOf(Color(0xFF3A2D7A), Color(0xFF1F5C8A), Color(0xFF6E3A8C)),
    Color(0xFFE6ECFF), 1f,
)
private val Dawn = SkyPalette(
    Color(0xFF2B2F6B), Color(0xFFF2A68A),
    listOf(Color(0xFFE56E80), Color(0xFFF2B56E), Color(0xFF7E5BA8)),
    Color(0xFFFFD9A3), 0.35f,
)
private val Day = SkyPalette(
    Color(0xFF2F7FD0), Color(0xFFB9DFF0),
    listOf(Color(0xFF4FB5BF), Color(0xFFE9CF86), Color(0xFF6F98DE)),
    Color(0xFFFFF4D6), 0f,
)
private val Dusk = SkyPalette(
    Color(0xFF34266A), Color(0xFFEE8657),
    listOf(Color(0xFFD5506D), Color(0xFFF09A48), Color(0xFF573C98)),
    Color(0xFFFFB27A), 0.45f,
)

// Used until the first weather response arrives (roughly a typical equinox).
private val DefaultSunrise = LocalTime.of(7, 0)
private val DefaultSunset = LocalTime.of(18, 30)

/** Night → dawn → day → dusk → night, keyed to the real sunrise and sunset. */
fun skyPalette(now: LocalTime, sunrise: LocalTime?, sunset: LocalTime?): SkyPalette {
    val m = now.toSecondOfDay() / 60f
    val rise = (sunrise ?: DefaultSunrise).toSecondOfDay() / 60f
    val set = (sunset ?: DefaultSunset).toSecondOfDay() / 60f
    val keys = listOf(
        rise - 60 to Night,
        rise to Dawn,
        rise + 90 to Day,
        set - 90 to Day,
        set to Dusk,
        set + 60 to Night,
    )
    if (m <= keys.first().first || m >= keys.last().first) return Night
    val i = keys.indexOfLast { it.first <= m }
    val (t0, p0) = keys[i]
    val (t1, p1) = keys[i + 1]
    return p0.lerpTo(p1, ((m - t0) / (t1 - t0)).coerceIn(0f, 1f))
}

/** How far the sun (by day) or moon (by night) is along its arc: 0 at the left horizon, 1 at the right. */
fun celestialProgress(now: LocalTime, sunrise: LocalTime?, sunset: LocalTime?): Float {
    val m = now.toSecondOfDay() / 60f
    val rise = (sunrise ?: DefaultSunrise).toSecondOfDay() / 60f
    val set = (sunset ?: DefaultSunset).toSecondOfDay() / 60f
    return if (m in rise..set) {
        (m - rise) / (set - rise)
    } else {
        val nightLength = 24 * 60 - (set - rise)
        val sinceSet = if (m > set) m - set else m + 24 * 60 - set
        sinceSet / nightLength
    }
}

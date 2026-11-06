package com.cleantv.launcher.data

import androidx.compose.ui.graphics.Color
import com.cleantv.launcher.BuildConfig

/**
 * One of the user's subscriptions.
 *
 * @param tmdbNames normalised fragments matched against TMDB provider names (which differ per region,
 *   e.g. "Amazon Prime Video" vs "Amazon Video"); empty when TMDB doesn't list the service.
 * @param packages known Android TV package names, tried in order before falling back to [labelHint].
 */
enum class StreamingService(
    val label: String,
    val color: Color,
    val tmdbNames: List<String>,
    val packages: List<String>,
    val labelHint: String,
) {
    Netflix("Netflix", Color(0xFFE50914), listOf("netflix"), listOf("com.netflix.ninja"), "netflix"),
    Prime(
        "Prime Video", Color(0xFF00A8E1), listOf("amazonprimevideo", "primevideo"),
        listOf("com.amazon.amazonvideo.livingroom"), "prime video",
    ),
    YouTube("YouTube", Color(0xFFFF0033), listOf("youtubepremium"), listOf("com.google.android.youtube.tv"), "youtube"),
    Spotify("Spotify", Color(0xFF1DB954), emptyList(), listOf("com.spotify.tv.android"), "spotify"),
    JioHotstar(
        "JioHotstar", Color(0xFF3B5BFF), listOf("hotstar"),
        listOf("in.startv.hotstar", "in.startv.hotstaronly"), "hotstar",
    ),
    SonyLiv("SonyLIV", Color(0xFFF2A900), listOf("sonyliv"), listOf("com.sonyliv"), "sony liv"),
    F1Tv("F1 TV", Color(0xFFE10600), listOf("f1tv"), listOf("com.formulaone.production"), "f1 tv"),
    Crunchyroll(
        "Crunchyroll", Color(0xFFF47521), listOf("crunchyroll"),
        listOf("com.crunchyroll.crunchyroid"), "crunchyroll",
    ),
    Zee5("Zee5", Color(0xFF8230C6), listOf("zee5"), listOf("com.graymatrix.did"), "zee5"),
    AppleTv("Apple TV+", Color(0xFFB8BEC4), listOf("appletv"), listOf("com.apple.atve.sony.appletv", "com.apple.atve.androidtv.appletv"), "apple tv");

    fun matchesProvider(providerName: String): Boolean {
        val n = normalise(providerName)
        return tmdbNames.any { n.contains(it) }
    }

    companion object {
        // "Apple TV+" → "appletvplus", "Sony LIV" → "sonyliv"
        fun normalise(name: String) = name.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }
    }
}

/** A TMDB "where to watch" region. The list comes from the `regions` setting; the first is the one used on launch. */
data class Region(val code: String, val label: String) {
    companion object {
        val all = BuildConfig.REGIONS.split(',').mapNotNull { entry ->
            val parts = entry.trim().split(':')
            parts.getOrNull(1)?.let { Region(parts[0].trim(), it.trim()) }
        }.ifEmpty { listOf(Region("US", "United States")) }
        val default = all.first()
    }
}

package com.cleantv.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import java.util.Locale

/**
 * "[IMDb] 8.9 · 2020 · Series · Comedy, Drama". Falls back to TMDB's community score (★) when the title
 * has no IMDb rating.
 */
@Composable
fun MetaLine(media: Media, imdbRating: Double?, genres: List<String> = emptyList(), fontSize: TextUnit = 12.sp) {
    val dim = Color.White.copy(alpha = 0.72f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (imdbRating != null) {
            Text(
                "IMDb",
                modifier = Modifier
                    .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp),
                color = Color.Black,
                fontSize = fontSize * 0.85f,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(6.dp))
            Text(format(imdbRating), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            listOfNotNull(
                media.year,
                if (media.type == MediaType.Tv) "Series" else "Movie",
                media.rating.takeIf { imdbRating == null && it > 0 }?.let { "★ " + format(it) },
                genres.take(3).joinToString(", ").ifEmpty { null },
            ).joinToString("  ·  "),
            color = dim,
            fontSize = fontSize,
        )
    }
}

private fun format(rating: Double) = String.format(Locale.US, "%.1f", rating)

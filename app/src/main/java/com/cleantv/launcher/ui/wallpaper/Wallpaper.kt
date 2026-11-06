package com.cleantv.launcher.ui.wallpaper

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.PerfFlags
import com.cleantv.launcher.data.wallpaper.Photo
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class WallpaperStyle(val label: String, val photo: Boolean = false) {
    Dunes("Dunes"),
    Aurora("Aurora"),
    Orbs("Orbs"),
    Daily("Daily", photo = true),
    Photos("Photos", photo = true),
    Diwali("Diwali"),
}

/**
 * Slow generative art behind the home screen. Everything is drawn from [palette], so the same scene
 * shifts from night to dawn to day to dusk. [celestial] places the sun or moon along its arc (0..1).
 */
@Composable
fun Wallpaper(
    style: WallpaperStyle,
    palette: SkyPalette,
    celestial: Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    if (style.photo) return PhotoWallpaper(style, palette, celestial, modifier)
    val time = rememberWallpaperClock(animate)
    val stars = remember { Random(7).let { r -> List(90) { Star(r.nextFloat(), r.nextFloat() * 0.6f, r.nextFloat()) } } }
    Canvas(modifier) {
        val t = time.value
        if (style == WallpaperStyle.Diwali) {
            // Festival night, whatever the hour.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF12061F), Color(0xFF3A0F2E), Color(0xFF5A1E14))))
            drawStars(stars, 1f, t)
        } else {
            drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom)))
            drawStars(stars, palette.night, t)
        }
        when (style) {
            WallpaperStyle.Dunes -> drawDunes(palette, celestial, t)
            WallpaperStyle.Aurora -> drawAurora(palette, t)
            WallpaperStyle.Orbs -> drawOrbs(palette, t)
            WallpaperStyle.Diwali -> drawDiwali(t)
            WallpaperStyle.Daily, WallpaperStyle.Photos -> Unit // photos are drawn by PhotoWallpaper
        }
    }
}

/**
 * Seconds since start, advanced at ~15 fps. The drift is slow enough that this looks continuous, and each
 * wallpaper frame costs the test TV ~30 ms of render-thread + GPU time, so drawing more often only causes jank.
 */
@Composable
private fun rememberWallpaperClock(running: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running || PerfFlags.staticWallpaper) return@LaunchedEffect
        // Resume from where it paused rather than jumping.
        val start = withFrameNanos { it } - (t.floatValue * 1e9f).toLong()
        var last = start
        while (true) {
            withFrameNanos { now ->
                if (now - last >= 66_000_000L) {
                    t.floatValue = (now - start) / 1e9f
                    last = now
                }
            }
        }
    }
    return t
}

private class Star(val x: Float, val y: Float, val phase: Float)

private fun DrawScope.drawStars(stars: List<Star>, night: Float, t: Float) {
    if (night < 0.05f) return
    stars.forEach { s ->
        val twinkle = 0.55f + 0.45f * sin(t * 0.8f + s.phase * 20f)
        drawCircle(
            Color.White.copy(alpha = night * twinkle * 0.85f),
            radius = 1.2f + s.phase * 1.8f,
            center = Offset(s.x * size.width, s.y * size.height),
        )
    }
}

private fun DrawScope.drawCelestial(palette: SkyPalette, progress: Float) {
    val c = Offset(
        size.width * (0.12f + 0.76f * progress),
        size.height * (0.44f - 0.24f * sin(progress * PI.toFloat())),
    )
    val r = size.height * 0.06f
    drawCircle(
        Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 4f),
        radius = r * 4f,
        center = c,
    )
    drawCircle(palette.glow, radius = r, center = c)
}

/** Layered hills drifting at different speeds, with the sun or moon behind them. */
private fun DrawScope.drawDunes(palette: SkyPalette, celestial: Float, t: Float) {
    drawCelestial(palette, celestial)
    val shadow = lerp(palette.skyTop, Color.Black, 0.55f)
    val layers = 4
    for (i in 0 until layers) {
        val depth = i / (layers - 1f) // 0 = far, 1 = near
        val color = lerp(palette.accents[i % palette.accents.size], shadow, 0.25f + depth * 0.55f)
        // Hills rise into the open sky above the shelf; the far ridge doubles as the horizon.
        val baseY = size.height * (0.34f + depth * 0.14f)
        val amp = size.height * (0.035f + depth * 0.025f)
        val speed = 0.01f + depth * 0.02f
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width + 8f) {
                val u = x / size.width
                val y = baseY +
                    amp * sin((u * (2.2f + i) + t * speed + i * 1.7f) * 2 * PI.toFloat()) +
                    amp * 0.5f * sin((u * (5.1f + i) - t * speed * 1.6f) * 2 * PI.toFloat())
                lineTo(x, y)
                x += 16f
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(color, lerp(color, shadow, 0.6f)), startY = baseY - amp, endY = size.height))
    }
}

/** Soft ribbons of light that ripple slowly across the upper sky. */
private fun DrawScope.drawAurora(palette: SkyPalette, t: Float) {
    palette.accents.forEachIndexed { i, color ->
        val centerY = size.height * (0.28f + i * 0.13f)
        val thickness = size.height * (0.16f + 0.04f * i)
        val path = Path()
        var x = 0f
        val top = mutableListOf<Offset>()
        while (x <= size.width + 10f) {
            val u = x / size.width
            val y = centerY + size.height * 0.08f * sin((u * 1.4f + t * (0.015f + i * 0.006f) + i) * 2 * PI.toFloat())
            top += Offset(x, y)
            x += 20f
        }
        path.moveTo(top.first().x, top.first().y)
        top.forEach { path.lineTo(it.x, it.y) }
        top.asReversed().forEach { path.lineTo(it.x, it.y + thickness) }
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent),
                startY = centerY - size.height * 0.08f,
                endY = centerY + thickness + size.height * 0.08f,
            ),
        )
    }
}

/** Large soft colour fields that wander slowly around the screen. */
private fun DrawScope.drawOrbs(palette: SkyPalette, t: Float) {
    val spots = listOf(
        Triple(0.22f, 0.35f, 0.55f),
        Triple(0.72f, 0.30f, 0.65f),
        Triple(0.50f, 0.78f, 0.50f),
        Triple(0.88f, 0.80f, 0.40f),
    )
    spots.forEachIndexed { i, (bx, by, br) ->
        val c = Offset(
            size.width * (bx + 0.08f * sin(t * 0.05f + i * 2f)),
            size.height * (by + 0.08f * sin(t * 0.04f + i * 3f)),
        )
        val r = size.height * br
        val color = palette.accents[i % palette.accents.size]
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.75f), Color.Transparent), center = c, radius = r), radius = r, center = c)
    }
}

/**
 * A photo wallpaper: Bing's photo of the day, or a featured landscape that changes every hour. It follows the
 * day too: dimmed and cooled after sunset, by the same sky palette the animated ones use. If the photo can't be
 * fetched (offline), the Dunes art shows instead.
 */
@Composable
private fun PhotoWallpaper(style: WallpaperStyle, palette: SkyPalette, celestial: Float, modifier: Modifier) {
    val photos = (LocalContext.current.applicationContext as CleanTvApplication).photos
    val photo by produceState<Photo?>(null, style) {
        while (true) {
            value = runCatching {
                if (style == WallpaperStyle.Daily) photos.daily() else photos.curated(LocalDateTime.now().hour)
            }.getOrNull() ?: value
            delay(if (value == null) 60_000L else 15 * 60_000L)
        }
    }
    val shown = photo ?: return Wallpaper(WallpaperStyle.Dunes, palette, celestial, modifier)
    Box(modifier) {
        Crossfade(shown.url, animationSpec = tween(1200), label = "photo") { url ->
            AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Night: darker and a little blue; day: a light veil so the UI stays readable on bright photos.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f + 0.45f * palette.night)))
        Box(Modifier.fillMaxSize().background(Color(0xFF0A1440).copy(alpha = 0.22f * palette.night)))
        if (shown.credit.isNotBlank()) {
            Text(
                shown.credit,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 132.dp, end = 48.dp).widthIn(max = 420.dp),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Diwali night: slow fireworks blooming high up, sparks drifting up, and a row of flickering diyas. */
private fun DrawScope.drawDiwali(t: Float) {
    val w = size.width
    val h = size.height
    // Fireworks: each burst grows and fades over 6 s, staggered, at fixed spots so it reads as calm.
    val bursts = listOf(
        Triple(0.18f, 0.22f, Color(0xFFFFC94D)), Triple(0.52f, 0.14f, Color(0xFFFF6FA8)),
        Triple(0.82f, 0.26f, Color(0xFF7FE3FF)), Triple(0.36f, 0.34f, Color(0xFFB48CFF)),
    )
    bursts.forEachIndexed { i, (bx, by, color) ->
        val phase = ((t + i * 1.6f) % 6f) / 6f
        val radius = h * 0.13f * phase
        val alpha = (1f - phase).coerceIn(0f, 1f)
        val c = Offset(w * bx, h * by)
        for (k in 0 until 14) {
            val a = k / 14f * 2 * PI.toFloat()
            val p = Offset(c.x + cos(a) * radius, c.y + sin(a) * radius + radius * radius / (h * 0.6f))
            drawCircle(color.copy(alpha = alpha * 0.9f), radius = 2.6f, center = p)
        }
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.25f), Color.Transparent), center = c, radius = radius + 1f),
            radius = radius + 1f,
            center = c,
        )
    }
    // Sparks rising from the diyas.
    for (k in 0 until 26) {
        val x = w * ((k * 0.37f + 0.05f) % 1f)
        val y = h * (0.95f - ((t * 0.03f + k * 0.13f) % 1f) * 0.6f)
        drawCircle(Color(0xFFFFC94D).copy(alpha = 0.35f + 0.3f * sin(t * 2 + k)), radius = 1.6f + (k % 3), center = Offset(x, y))
    }
    // Diyas along the bottom: a clay bowl, a warm glow and a flickering flame.
    val count = 9
    for (k in 0 until count) {
        val x = w * (k + 0.5f) / count
        val baseY = h * 0.93f
        val flicker = 1f + 0.12f * sin(t * 9 + k * 1.7f) + 0.06f * sin(t * 17 + k)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.45f), Color.Transparent), center = Offset(x, baseY - h * 0.03f), radius = h * 0.09f),
            radius = h * 0.09f,
            center = Offset(x, baseY - h * 0.03f),
        )
        val bowl = Path().apply {
            moveTo(x - h * 0.035f, baseY - h * 0.012f)
            quadraticTo(x, baseY + h * 0.03f, x + h * 0.035f, baseY - h * 0.012f)
            close()
        }
        drawPath(bowl, Color(0xFFB4532A))
        val fh = h * 0.035f * flicker
        val flame = Path().apply {
            moveTo(x, baseY - h * 0.012f - fh)
            quadraticTo(x + h * 0.012f, baseY - h * 0.02f, x, baseY - h * 0.01f)
            quadraticTo(x - h * 0.012f, baseY - h * 0.02f, x, baseY - h * 0.012f - fh)
            close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFF9F1C)), startY = baseY - h * 0.012f - fh, endY = baseY - h * 0.01f))
    }
}

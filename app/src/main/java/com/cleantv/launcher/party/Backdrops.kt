package com.cleantv.launcher.party

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Game night's settings. A different one comes up with each game, so the room keeps changing. */
enum class Backdrop { Theatre, Rangoli, Toran }

/** Draws at about 15 frames a second, which is plenty for slow movement and leaves the TV room for the game. */
@Composable
fun GameBackdrop(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            withFrameNanos { now ->
                if (now - last >= 66_000_000L) {
                    time.floatValue = (now - start) / 1e9f
                    last = now
                }
            }
        }
    }
    val flowers = remember { Random(11).let { r -> List(60) { Offset(r.nextFloat(), r.nextFloat()) } } }
    Canvas(modifier) {
        val t = time.floatValue
        when (backdrop) {
            Backdrop.Theatre -> drawTheatre(t)
            Backdrop.Rangoli -> drawRangoli(t)
            Backdrop.Toran -> drawToran(t, flowers)
        }
    }
}

/** An old single-screen cinema: velvet curtain, a scalloped gold pelmet with chasing bulbs, two slow spotlights. */
private fun DrawScope.drawTheatre(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFF3D0710), Color(0xFF8E1525), Color(0xFF4B0912)),
            start = Offset.Zero, end = Offset(w / 11f, 0f), tileMode = TileMode.Mirror,
        ),
    )
    // Darker in the middle, where the game sits.
    drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.3f)), Offset(w / 2, h * 0.55f), w * 0.62f))
    listOf(0.13f to 0.31f, 0.21f to -0.27f).forEach { (speed, side) ->
        val x = w * (0.5f + side * sin(t * speed + side * 9f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0x33FFE3A0), Color.Transparent), Offset(x, h * 0.72f), h * 0.42f),
            h * 0.42f, Offset(x, h * 0.72f),
        )
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC120306)), startY = h * 0.78f, endY = h))
    // Kept above the title and scores.
    val scallops = 30
    val r = w / scallops / 2f
    val band = h * 0.008f
    drawRect(Color(0xFF5E0C18), size = Size(w, band))
    for (i in 0 until scallops) drawCircle(Color(0xFF5E0C18), r, Offset(r + i * r * 2, band))
    for (i in 0 until scallops) {
        val c = Offset(r + i * r * 2, band)
        drawArc(Color(0xFFE2B04A), 0f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.004f))
        val lit = (i + (t * 2.5f).toInt()) % 3 == 0
        drawCircle(if (lit) Color(0xFFFFE9A8) else Color(0xFF8A6A2A), h * 0.005f, Offset(c.x, c.y + r))
    }
}

/** A huge rangoli turning very slowly on a dark floor, its rings going opposite ways. */
private fun DrawScope.drawRangoli(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(Color(0xFF1C1140), Color(0xFF07040F)), Offset(w / 2, h / 2), w * 0.7f))
    val c = Offset(w / 2, h / 2)
    val unit = h * 0.62f
    fun petal(len: Float, wide: Float) = Path().apply {
        moveTo(0f, 0f)
        quadraticBezierTo(wide, -len * 0.5f, 0f, -len)
        quadraticBezierTo(-wide, -len * 0.5f, 0f, 0f)
    }
    val rings = listOf(
        Triple(16, unit * 1.12f, Color(0xFF7A1F6B)) to 1.2f,
        Triple(12, unit * 0.86f, Color(0xFFB8860B)) to -1.7f,
        Triple(16, unit * 0.62f, Color(0xFF0F6E78)) to 2.3f,
        Triple(8, unit * 0.40f, Color(0xFFB4324F)) to -3f,
    )
    rings.forEach { (ring, speed) ->
        val (count, len, color) = ring
        val shape = petal(len, len * 0.22f)
        val base = t * speed
        for (i in 0 until count) {
            withTransform({
                translate(c.x, c.y)
                rotate(base + i * 360f / count, Offset.Zero)
            }) { drawPath(shape, color.copy(alpha = 0.38f)) }
        }
    }
    listOf(Triple(24, unit * 0.98f, Color(0xFFFFD66B)), Triple(16, unit * 0.5f, Color(0xFFFFF3DA))).forEachIndexed { n, (count, radius, color) ->
        for (i in 0 until count) {
            val a = (i.toFloat() / count + t * 0.004f * (if (n == 0) 1 else -1)) * 2 * PI.toFloat()
            val glow = 0.45f + 0.4f * sin(t * 1.6f + i)
            drawCircle(color.copy(alpha = glow), h * 0.009f, Offset(c.x + radius * cos(a), c.y + radius * sin(a)))
        }
    }
    drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), c, h * 0.7f))
}

/** A courtyard at dusk: marigold strings swaying from the top and petals drifting down. */
private fun DrawScope.drawToran(t: Float, flowers: List<Offset>) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B0B3F), Color(0xFF7C1F4F), Color(0xFFC2502C))))
    drawRect(Color.Black.copy(alpha = 0.22f))
    // A small garland along the very top, and full strings down the sides, clear of the title and the game.
    val across = 44
    for (i in 0 until across) {
        val x = w * (i + 0.5f) / across
        drawCircle(if (i % 2 == 0) Color(0xFFFF9F1C) else Color(0xFFFFD23F), h * 0.012f, Offset(x, h * 0.016f))
    }
    val xs = listOf(0.025f, 0.075f, 0.125f, 0.875f, 0.925f, 0.975f)
    xs.forEachIndexed { s, at ->
        val x = w * at
        val length = 11 + (s * 5 % 7)
        val sway = sin(t * 0.8f + s * 0.9f) * w * 0.004f
        for (f in 0 until length) {
            val fy = h * (0.14f + f * 0.038f)
            val fx = x + sway * (f.toFloat() / 4f)
            val orange = (f + s) % 2 == 0
            drawCircle(if (orange) Color(0xFFFF9F1C) else Color(0xFFFFD23F), h * 0.019f, Offset(fx, fy))
            drawCircle(Color(0xFFD9620A), h * 0.007f, Offset(fx, fy))
        }
        drawCircle(Color(0xFFFFF3DA), h * 0.008f, Offset(x + sway * length / 4f, h * (0.14f + length * 0.038f)))
    }
    flowers.forEachIndexed { i, p ->
        val fall = (p.y + t * (0.018f + (i % 5) * 0.004f)) % 1f
        val x = (p.x + 0.015f * sin(t * 0.7f + i)) * w
        rotate(t * 25f + i * 40f, Offset(x, fall * h)) {
            drawOval(
                (if (i % 3 == 0) Color(0xFFFFB627) else Color(0xFFFF7A59)).copy(alpha = 0.75f),
                Offset(x - h * 0.008f, fall * h - h * 0.004f), Size(h * 0.016f, h * 0.008f),
            )
        }
    }
}

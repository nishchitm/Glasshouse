package com.cleantv.launcher.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cleantv.launcher.PerfFlags
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * One glass "recipe". The prototype shows several side by side so the look can be picked on the TV.
 *
 * @param tint base tint laid over the blur; ignored in favour of the ambient colour when [ambient] is true.
 * @param rim brightness of the specular edge (0..1).
 * @param sheen brightness of the top-down light falloff inside the panel (0..1).
 */
data class GlassSpec(
    val name: String,
    val tagline: String,
    val blur: Dp,
    val tint: Color,
    val tintAlpha: Float,
    val noise: Float,
    val rim: Float,
    val sheen: Float,
    val ambient: Boolean = false,
)

val GlassVariants = listOf(
    GlassSpec("Clear", "Thin, bright, mostly refraction", 16.dp, Color.White, 0.06f, 0.00f, 0.60f, 0.20f),
    GlassSpec("Frost", "Milky, soft, high diffusion", 40.dp, Color.White, 0.18f, 0.12f, 0.35f, 0.10f),
    GlassSpec("Smoke", "Dark, cinematic, low glare", 32.dp, Color.Black, 0.38f, 0.06f, 0.28f, 0.06f),
    GlassSpec("Aura", "Tinted by the focused artwork", 28.dp, Color.White, 0.24f, 0.08f, 0.48f, 0.14f, ambient = true),
)

/** Frosted glass panel: blurred backdrop + tint + noise, then a specular rim and top sheen. */
fun Modifier.glass(
    state: HazeState,
    spec: GlassSpec,
    shape: Shape,
    ambientColor: Color = Color.White,
): Modifier {
    val tint = if (spec.ambient) ambientColor else spec.tint
    if (PerfFlags.noBlur) return frostedGlass(spec, shape)
    return this
        .clip(shape)
        .hazeChild(
            state = state,
            style = HazeStyle(
                backgroundColor = Color.Black,
                tints = listOf(HazeTint(tint.copy(alpha = spec.tintAlpha))),
                blurRadius = spec.blur,
                noiseFactor = spec.noise,
            ),
        ) {
            // Haze only enables blur by default on API 32+; the test TV is API 31, where RenderEffect exists.
            blurEnabled = true
        }
        .glassLight(spec)
        .border(width = 1.5.dp, brush = rimBrush(spec), shape = shape)
}

/**
 * The same glass look without a live blur: a translucent fill, light falloff and specular rim. For small
 * elements over moving content, where re-blurring the backdrop every frame costs the TV's GPU more than
 * the blur is worth (measured on the test TV: ~11 ms a frame for the three top-bar buttons).
 */
fun Modifier.frostedGlass(spec: GlassSpec, shape: Shape): Modifier = this
    .clip(shape)
    .background(spec.tint.copy(alpha = (spec.tintAlpha + 0.12f).coerceAtMost(1f)))
    .glassLight(spec)
    .border(width = 1.5.dp, brush = rimBrush(spec), shape = shape)

private fun rimBrush(spec: GlassSpec) = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = spec.rim),
        Color.White.copy(alpha = spec.rim * 0.12f),
        Color.White.copy(alpha = spec.rim * 0.45f),
    ),
)

/** Light falling from above: brighter at the top edge, fading out by mid-height. */
private fun Modifier.glassLight(spec: GlassSpec): Modifier = drawWithContent {
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = spec.sheen),
            0.45f to Color.Transparent,
        ),
    )
    drawContent()
}

/** The look picked from the prototype. */
val DefaultGlass = GlassVariants.first { it.name == "Smoke" }

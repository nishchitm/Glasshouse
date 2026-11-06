package com.cleantv.launcher.party

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Coming into the party: velvet curtains closed over the screen with the welcome on them, then parting.
 * @param hold how long the welcome stays up first; the first arrival of the night gets the long one.
 */
@Composable
internal fun BoxScope.WelcomeCurtain(hold: Long) {
    var done by remember { mutableStateOf(false) }
    if (done) return
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(hold)
        open.animateTo(1f, tween(1_200, easing = FastOutSlowInEasing))
        done = true
    }
    val velvet = Brush.linearGradient(
        listOf(Color(0xFF3D0710), Color(0xFF9A1728), Color(0xFF4B0912)),
        start = Offset.Zero, end = Offset(120f, 0f), tileMode = TileMode.Mirror,
    )
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { translationX = -open.value * size.width }.background(velvet))
        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { translationX = open.value * size.width }.background(velvet))
    }
    Column(
        Modifier.align(Alignment.Center).graphicsLayer {
            alpha = (1f - open.value * 2.5f).coerceAtLeast(0f)
            scaleX = 1f + open.value * 0.3f
            scaleY = 1f + open.value * 0.3f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🪔", fontSize = 40.sp)
        Spacer(Modifier.height(6.dp))
        Text("WELCOME TO", color = Paper.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        if (PartyName.hosts.isNotEmpty()) {
            Text(PartyName.hosts, color = Marigold, fontSize = 24.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
        }
        Text(PartyName.title.uppercase(), color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text("Games, filmi drama and terrible decisions", color = Paper.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
    }
}

private class Spark(val angle: Float, val reach: Float, val size: Float, val color: Color)

/** Party mode switching: a burst of sparks and a card that pops up, then all of it clears itself away. */
@Composable
internal fun BoxScope.PartyPopup(on: Boolean, lights: Boolean, onDone: () -> Unit) {
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val sparks = remember {
        val colors = if (on) listOf(Marigold, Color(0xFFFF7A59), Color(0xFFFFF3DA), Color(0xFFC86BFF)) else listOf(Paper, Color(0xFFB9B4C7))
        List(if (on) 70 else 24) { Spark(Random.nextFloat() * 6.283f, 0.35f + Random.nextFloat() * 0.65f, 2.5f + Random.nextFloat() * 3.5f, colors.random()) }
    }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1_500, easing = LinearEasing)) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(1_900)
        pop.animateTo(0f, tween(300))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val t = burst.value
        if (t < 1f) sparks.forEach { s ->
            val d = s.reach * size.width * 0.55f * (1f - (1f - t) * (1f - t))
            drawCircle(s.color.copy(alpha = 1f - t), s.size * density, Offset(c.x + d * cos(s.angle), c.y + d * sin(s.angle) + t * t * size.height * 0.12f))
        }
    }
    Column(
        Modifier.align(Alignment.Center).width(420.dp)
            .graphicsLayer { alpha = pop.value.coerceIn(0f, 1f); scaleX = pop.value; scaleY = pop.value }
            .background(Paper, CardShape).border(3.dp, Marigold, CardShape).padding(vertical = 16.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (on) "✨" else "💡", fontSize = 34.sp)
        Text(if (on) "PARTY MODE ON" else "PARTY MODE OFF", color = Plum, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        val line = when {
            on && lights -> "The lights go gold. Dignity is now optional."
            on -> "Dignity is now optional."
            lights -> "Lights back to normal. Everyone act natural."
            else -> "Everyone act natural."
        }
        Text(line, color = Plum, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
    }
}

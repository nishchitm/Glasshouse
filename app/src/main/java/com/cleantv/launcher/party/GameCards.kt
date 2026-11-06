package com.cleantv.launcher.party

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val Paper = Color(0xFFFFF3DA)
internal val Plum = Color(0xFF3B1450)
internal val Marigold = Color(0xFFFFB31A)
internal val CardShape = RoundedCornerShape(20.dp)

/** Turns over from [front] to [back]. Drawn once and turned as a picture: redrawing the text at each angle stutters. */
@Composable
internal fun FlipCard(flipped: Boolean, modifier: Modifier, front: @Composable () -> Unit, back: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "flip")
    val showBack by remember { derivedStateOf { angle > 90f } }
    Box(
        modifier.graphicsLayer {
            rotationY = angle
            cameraDistance = 14 * density
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        if (showBack) Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { back() } else front()
    }
}

/**
 * Slides the old card out and the new one in when [key] changes. The old card stays frozen as it was, so it leaves
 * showing what it showed, not the next card's state. With [slide] off the two simply fade across.
 */
@Composable
internal fun <K> Deck(s: PartyState, key: K, modifier: Modifier, slide: Boolean = true, content: @Composable (PartyState) -> Unit) {
    AnimatedContent(
        key, modifier,
        transitionSpec = {
            if (slide) {
                (slideInHorizontally(tween(450)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(450)) { -it } + fadeOut(tween(300)))
            } else {
                fadeIn(tween(350)) togetherWith fadeOut(tween(250))
            } using SizeTransform(clip = false) // a sliding card isn't cut off at the edge of its slot
        },
        label = "deck",
    ) { k ->
        val held = remember { arrayOf(s) }
        if (k == key) held[0] = s
        content(held[0])
    }
}

/** A gold coin with what the card is worth right now, big enough to read from the sofa. */
@Composable
private fun WorthCoin(points: Double, modifier: Modifier = Modifier) {
    Column(
        modifier.rotate(-6f).size(70.dp).background(Brush.radialGradient(listOf(Color(0xFFFFF0B0), Marigold, Color(0xFFD08A10))), CircleShape).border(2.dp, Plum, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(points(points), color = Plum, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp, maxLines = 1)
        Text(if (points == 1.0) "POINT" else "POINTS", color = Plum, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, lineHeight = 9.sp)
    }
}

/** Each buzzer game's question has a look of its own; the answer side is shared. */
@Composable
internal fun GuessCard(s: PartyState, p: EmojiPuzzle, modifier: Modifier) {
    FlipCard(
        s.revealed, modifier,
        front = {
            Box(Modifier.fillMaxSize()) {
                when (s.mode) {
                    GuessMode.Movies -> TicketCard(s, p)
                    GuessMode.Dialogues -> TitleCard(s, p)
                    GuessMode.Cast -> PosterCard(s, p)
                    GuessMode.Plots -> PaperCard(s, p)
                }
                // The ticket prints its value on the stub instead.
                if (s.mode != GuessMode.Movies) WorthCoin(s.puzzleWorth, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 14.dp))
            }
        },
        back = { AnswerSide(s, p) },
    )
}

@Composable
private fun LevelTag(level: String) {
    if (level.isEmpty()) return
    val tag = when (level) {
        "Easy" -> Color(0xFF3FB56B)
        "Hard" -> Color(0xFFE5484D)
        else -> Marigold
    }
    Text(
        level.uppercase(), color = Plum, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.background(tag, CircleShape).padding(horizontal = 11.dp, vertical = 2.dp),
    )
}

@Composable
private fun Clues(s: PartyState, p: EmojiPuzzle, text: Color, chip: Color) {
    Hint.entries.filter { it in s.hints }.forEach { hint ->
        Spacer(Modifier.height(5.dp))
        Text(
            when (hint) {
                Hint.Song -> "🎵  ${p.song}"
                Hint.Title -> "🔤  ${p.titleHint}"
                Hint.Story -> "💡  ${p.story}"
            },
            color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(chip, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

/** Badly Explained: a typed-up plot on a paper card. */
@Composable
private fun PaperCard(s: PartyState, p: EmojiPuzzle) {
    Column(Modifier.fillMaxSize().background(Paper, CardShape).border(3.dp, Marigold, CardShape).clip(CardShape)) {
        Row(Modifier.fillMaxWidth().background(Plum).padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🪔  PLOT KA SATYANASH", color = Marigold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            LevelTag(p.level)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 30.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "“${p.emojis}”", color = Plum, fontSize = if (s.hints.isEmpty()) 25.sp else 20.sp,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
            Clues(s, p, Plum, Marigold.copy(alpha = 0.35f))
        }
    }
}

/** Emoji Movies: a cinema ticket with a torn-off stub and real punched notches. */
@Composable
private fun TicketCard(s: PartyState, p: EmojiPuzzle) {
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFFE27A), Marigold)), RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("★  EMOJI KA TAMASHA  ★", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text(p.emojis, fontSize = if (s.hints.size > 1) 56.sp else if (p.emojis.length > 10) 66.sp else 84.sp, textAlign = TextAlign.Center, maxLines = 1)
                Clues(s, p, Plum, Paper.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Text("Name this film. No refunds.", color = Plum.copy(alpha = 0.7f), fontSize = 11.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
            }
            Column(
                Modifier.width(112.dp).fillMaxHeight().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("WORTH", color = Plum, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(points(s.puzzleWorth), color = Plum, fontSize = if (s.puzzleWorth % 1.0 == 0.0) 64.sp else 34.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(if (s.puzzleWorth == 1.0) "POINT" else "POINTS", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                LevelTag(p.level)
            }
        }
        // The tear line and notches, punched right through to whatever is behind.
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width - 112.dp.toPx()
            drawLine(
                Plum.copy(alpha = 0.55f), Offset(x, 16.dp.toPx()), Offset(x, size.height - 16.dp.toPx()), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())), cap = StrokeCap.Round,
            )
            val notch = 13.dp.toPx()
            listOf(Offset(x, 0f), Offset(x, size.height), Offset(0f, size.height / 2), Offset(size.width, size.height / 2)).forEach {
                drawCircle(Color.Black, notch, it, blendMode = BlendMode.Clear)
            }
        }
    }
}

/** Famous Dialogues: a silent-film title card, the line filling the frame inside an ornate border. */
@Composable
private fun TitleCard(s: PartyState, p: EmojiPuzzle) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Brush.radialGradient(listOf(Color(0xFF3B2A17), Color(0xFF120C06))))) {
        Canvas(Modifier.fillMaxSize()) {
            val cream = Color(0xFFE9D9B0)
            val outer = 9.dp.toPx()
            val inner = 16.dp.toPx()
            drawRect(cream, Offset(outer, outer), Size(size.width - outer * 2, size.height - outer * 2), style = Stroke(2.5.dp.toPx()))
            drawRect(cream.copy(alpha = 0.55f), Offset(inner, inner), Size(size.width - inner * 2, size.height - inner * 2), style = Stroke(1.dp.toPx()))
            val curl = 22.dp.toPx()
            listOf(Offset(inner, inner), Offset(size.width - inner, inner), Offset(inner, size.height - inner), Offset(size.width - inner, size.height - inner)).forEach {
                drawCircle(cream, curl, it, style = Stroke(1.5.dp.toPx()))
                drawCircle(cream, 4.dp.toPx(), it)
            }
            // Old-print scratches.
            listOf(0.13f, 0.37f, 0.71f, 0.88f).forEach { x ->
                drawLine(cream.copy(alpha = 0.06f), Offset(size.width * x, 0f), Offset(size.width * (x + 0.012f), size.height), 1.dp.toPx())
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦  WHICH FILM SAID IT?  ✦", color = Marigold, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "\u201C${p.emojis}\u201D", color = Color(0xFFFFF3DA), fontSize = if (s.hints.size > 1) 23.sp else 31.sp,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, lineHeight = if (s.hints.size > 1) 29.sp else 39.sp,
            )
            Clues(s, p, Color.White, Color.White.copy(alpha = 0.16f))
            Spacer(Modifier.weight(1f))
            LevelTag(p.level)
        }
    }
}

/** Guess the Cast: a hand-painted poster with everything but the title. */
@Composable
private fun PosterCard(s: PartyState, p: EmojiPuzzle) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF8E1A17)).border(4.dp, Marigold, RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 1.05f)
            val reach = size.width
            for (i in 0 until 18) {
                val a = (i / 18f) * PI.toFloat() * 2
                val b = a + PI.toFloat() / 18
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + reach * cos(a), c.y + reach * sin(a))
                    lineTo(c.x + reach * cos(b), c.y + reach * sin(b))
                    close()
                }
                drawPath(path, Color(0xFFC8391F))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("STARRING", color = Marigold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                LevelTag(p.level)
            }
            Spacer(Modifier.weight(1f))
            val names = p.emojis.split(", ")
            names.forEach {
                Text(
                    it.uppercase(), color = Color.White, fontSize = if (names.size > 3 || s.hints.isNotEmpty()) 24.sp else 31.sp,
                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1,
                )
            }
            Clues(s, p, Plum, Paper.copy(alpha = 0.9f))
            Spacer(Modifier.weight(1f))
            Text("IN  ★ ? ? ? ★  NOW IN CINEMAS", color = Marigold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun AnswerSide(s: PartyState, p: EmojiPuzzle) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF6A2A7E), Color(0xFF250A33))), CardShape)
            .border(3.dp, Marigold, CardShape)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("THE FILM WAS", color = Marigold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(p.answer, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 42.sp)
        p.year?.let { Text("$it", color = Marigold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        val line = p.joke.ifEmpty { p.story }
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(line, color = Paper, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        val winner = s.player(s.buzzedBy)
        Text(
            if (s.awarded != null && winner != null) "${winner.name} +${points(s.awarded)} for ${TeamNames[winner.team]}" else "Nobody got this one",
            color = if (s.awarded != null && winner != null) TeamColors[winner.team] else Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** A short instruction on a strip of paper, rather than loose text on the background. */
@Composable
internal fun PaperStrip(text: String, color: Color = Plum) {
    Text(
        text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.background(Paper, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

/** A countdown as a ring that drains, red for the last ten seconds. */
@Composable
internal fun CountdownRing(seconds: Long, total: Long, ringSize: androidx.compose.ui.unit.Dp) {
    val color = if (seconds <= 10) Color(0xFFFF6B6B) else Marigold
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.08f
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawCircle(Color.Black.copy(alpha = 0.55f), size.minDimension / 2)
            drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * seconds / total, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$seconds", color = color, fontSize = (ringSize.value * 0.42f).sp, fontWeight = FontWeight.Light)
    }
}

/** A marquee of light bulbs around [content], chasing round slowly. */
@Composable
internal fun Marquee(chase: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF5B0E1C), Color(0xFF2A060E))), RoundedCornerShape(16.dp)).border(3.dp, Marigold, RoundedCornerShape(16.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val m = 13.dp.toPx()
            val across = 26
            val down = 12
            val dx = (size.width - m * 2) / across
            val dy = (size.height - m * 2) / down
            var n = 0
            fun bulb(x: Float, y: Float) {
                val lit = (n + chase) % 3 == 0
                drawCircle(if (lit) Color(0xFFFFE9A8) else Color(0xFF7A5A20), 3.5.dp.toPx(), Offset(x, y))
                n++
            }
            for (i in 0 until across) bulb(m + i * dx, m)
            for (i in 0 until down) bulb(size.width - m, m + i * dy)
            for (i in 0 until across) bulb(size.width - m - i * dx, size.height - m)
            for (i in 0 until down) bulb(m, size.height - m - i * dy)
        }
        Box(Modifier.fillMaxSize().padding(26.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** A film strip's sprocket holes either side of a picture. */
@Composable
internal fun FilmStrip(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Color(0xFF0D0D0F), RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val hole = Size(11.dp.toPx(), 15.dp.toPx())
            val gap = 27.dp.toPx()
            var y = 7.dp.toPx()
            while (y + hole.height < size.height) {
                drawRoundRect(Color(0xFFE9E3D3), Offset(7.dp.toPx(), y), hole, androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                drawRoundRect(Color(0xFFE9E3D3), Offset(size.width - 7.dp.toPx() - hole.width, y), hole, androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                y += gap
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 5.dp)) { content() }
    }
}

/** A stamp at an angle, as on a rejected form. */
@Composable
internal fun Stamp(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
        modifier = Modifier.rotate(-8f).border(2.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

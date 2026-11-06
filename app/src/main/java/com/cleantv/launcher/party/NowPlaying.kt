package com.cleantv.launcher.party

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** What the party's music app is playing right now, or null when it's quiet. */
@Composable
internal fun rememberNowPlaying(music: PartyMusic): PartyMusic.NowPlaying? {
    var now by remember { mutableStateOf<PartyMusic.NowPlaying?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = withContext(Dispatchers.IO) { music.now() }?.takeIf { it.playing }
            delay(2_000)
        }
    }
    return now
}

/** Album art, a dancing equaliser and the song, sliding in whenever music is on. */
@Composable
internal fun NowPlayingPill(now: PartyMusic.NowPlaying?, modifier: Modifier = Modifier) {
    // Kept while it slides away, so the words don't vanish first.
    var last by remember { mutableStateOf(now) }
    if (now != null) last = now
    AnimatedVisibility(now != null, modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
        val song = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .background(Color(0xE61A0E26), RoundedCornerShape(14.dp))
                .border(1.dp, Marigold.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = remember(song.art) { song.art?.asImageBitmap() }
            if (art != null) {
                Image(art, null, Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
            }
            Equaliser(Modifier.size(width = 18.dp, height = 20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 170.dp)) {
                Text("NOW PLAYING", color = Marigold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, lineHeight = 9.sp)
                Text(
                    song.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = 15.sp,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 22.dp),
                )
                if (song.artist.isNotEmpty()) {
                    Text(song.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, maxLines = 1, lineHeight = 11.sp)
                }
            }
        }
    }
}

/** Game night's version: one line along the bottom edge, in the margin every screen leaves free. */
@Composable
internal fun NowPlayingStrip(now: PartyMusic.NowPlaying?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(now) }
    if (now != null) last = now
    AnimatedVisibility(now != null, modifier, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        val song = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(bottom = 2.dp)
                .height(24.dp)
                .background(Color(0xE61A0E26), RoundedCornerShape(12.dp))
                .border(1.dp, Marigold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = remember(song.art) { song.art?.asImageBitmap() }
            if (art != null) {
                Image(art, null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(7.dp))
            }
            Equaliser(Modifier.size(width = 14.dp, height = 14.dp))
            Spacer(Modifier.width(7.dp))
            Text("NOW PLAYING", color = Marigold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.width(9.dp))
            Text(song.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp))
            if (song.artist.isNotEmpty()) {
                Text("  ·  ${song.artist}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
            }
        }
    }
}

/** Four bars bouncing at their own speeds. Not the real audio: Android doesn't share that. */
@Composable
private fun Equaliser(modifier: Modifier) {
    val clock by rememberInfiniteTransition(label = "eq").animateFloat(
        0f, 62.83f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "eq",
    )
    Canvas(modifier) {
        val bar = size.width / 6f
        listOf(1.0f, 1.7f, 1.3f, 2.1f).forEachIndexed { i, speed ->
            val level = 0.3f + 0.7f * (0.5f + 0.5f * sin(clock * speed + i * 1.4f))
            val h = size.height * level
            drawRoundRect(
                Marigold, Offset(i * bar * 1.65f, size.height - h), Size(bar, h), CornerRadius(bar / 2),
            )
        }
    }
}

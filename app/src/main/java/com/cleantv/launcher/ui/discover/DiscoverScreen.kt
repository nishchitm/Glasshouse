package com.cleantv.launcher.ui.discover

import com.cleantv.launcher.data.Region
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cleantv.launcher.data.taste.Signal
import com.cleantv.launcher.data.tmdb.Availability
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import com.cleantv.launcher.ui.common.AvailabilityRow
import com.cleantv.launcher.ui.common.WatchOnPicker
import com.cleantv.launcher.ui.common.MetaLine
import com.cleantv.launcher.ui.glass.DefaultGlass
import com.cleantv.launcher.ui.glass.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Full-screen swipe deck, driven by the D-pad: ◀ not for me, ▶ like, ▲ watchlist, ▼ already seen,
 * OK opens it on a service, Back returns home.
 */
@Composable
fun DiscoverScreen(onExit: () -> Unit, vm: DiscoverViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hazeState = remember { HazeState() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf<Signal?>(null) }
    val top = state.deck.firstOrNull()

    BackHandler(onBack = onExit)
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun act(signal: Signal) {
        val media = top ?: return
        if (leaving != null) return // one card at a time
        leaving = signal
        scope.launch {
            val (x, y) = when (signal) {
                Signal.Like -> 1500f to 0f
                Signal.Nope -> -1500f to 0f
                Signal.Watchlist -> 0f to -1000f
                Signal.Seen -> 0f to 1000f
            }
            coroutineScope {
                launch { offsetX.animateTo(x, tween(320)) }
                launch { offsetY.animateTo(y, tween(320)) }
            }
            vm.rate(media, signal)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            leaving = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val signal = when (e.key) {
                    Key.DirectionLeft -> Signal.Nope
                    Key.DirectionRight -> Signal.Like
                    Key.DirectionUp -> Signal.Watchlist
                    Key.DirectionDown -> Signal.Seen
                    Key.DirectionCenter, Key.Enter -> null
                    else -> return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) {
                    if (signal != null) act(signal) else top?.let(vm::play)
                }
                true
            },
    ) {
        // Blurred copy of the current card's art fills the screen behind the deck.
        Box(Modifier.fillMaxSize().haze(hazeState)) {
            Crossfade(top?.backdropUrl, animationSpec = tween(500), label = "bg") { url ->
                if (url != null) {
                    AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(40.dp))
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Discover", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Text(
                    "${state.rated} rated · picks for ${Region.default.label}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("Back  Home", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    top != null -> {
                        state.deck.getOrNull(1)?.let { next ->
                            DeckCard(
                                next, state.availability[next.key], state.imdb[next.key], state.genreNames[next.type].orEmpty(),
                                Modifier.graphicsLayer {
                                    scaleX = 0.94f
                                    scaleY = 0.94f
                                    translationY = 14.dp.toPx()
                                    alpha = 0.55f
                                },
                            )
                        }
                        val progress = (maxOf(abs(offsetX.value) / 500f, abs(offsetY.value) / 350f)).coerceIn(0f, 1f)
                        DeckCard(
                            top, state.availability[top.key], state.imdb[top.key], state.genreNames[top.type].orEmpty(),
                            Modifier.graphicsLayer {
                                translationX = offsetX.value
                                translationY = offsetY.value
                                rotationZ = offsetX.value / 60f
                            },
                            stamp = leaving,
                            stampAlpha = progress,
                        )
                    }
                    else -> Text(
                        state.message ?: if (state.loading) "Finding things you'll like…" else "",
                        color = Color.White,
                        fontSize = 22.sp,
                    )
                }
            }

            state.watchOn?.let { choice -> WatchOnPicker(choice, vm::playOn, vm::closeWatchOn) }
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Hint(hazeState, "◀", "Not for me")
                Hint(hazeState, "▲", "Watchlist")
                Hint(hazeState, "▼", "Seen it")
                Hint(hazeState, "▶", "Like")
                Hint(hazeState, "OK", "Play")
            }
        }
    }
}

@Composable
private fun DeckCard(
    media: Media,
    availability: Availability?,
    imdbRating: Double?,
    genreNames: Map<Int, String>,
    modifier: Modifier,
    stamp: Signal? = null,
    stampAlpha: Float = 0f,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .size(width = 608.dp, height = 342.dp)
            .clip(shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape),
    ) {
        AsyncImage(media.backdropUrl, media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(
                media.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(media, imdbRating, media.genreIds.mapNotNull { genreNames[it] })
            Spacer(Modifier.height(6.dp))
            Text(
                media.overview,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            AvailabilityRow(availability, Region.default.label)
        }
        if (stamp != null) Stamp(stamp, stampAlpha)
    }
}

@Composable
private fun BoxScope.Stamp(signal: Signal, alpha: Float) {
    val (label, color, align) = when (signal) {
        Signal.Like -> Triple("LIKE", Color(0xFF4CD964), Alignment.TopStart)
        Signal.Nope -> Triple("NOPE", Color(0xFFFF4D5E), Alignment.TopEnd)
        Signal.Watchlist -> Triple("WATCHLIST", Color(0xFFFFC94D), Alignment.TopCenter)
        Signal.Seen -> Triple("SEEN IT", Color(0xFF6CB8FF), Alignment.TopCenter)
    }
    Text(
        label,
        modifier = Modifier
            .align(align)
            .padding(28.dp)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = if (signal == Signal.Nope) 12f else -12f
            }
            .border(BorderStroke(3.dp, color), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = color,
        fontSize = 24.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun Hint(hazeState: HazeState, key: String, label: String) {
    Row(
        Modifier
            .glass(hazeState, DefaultGlass, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}


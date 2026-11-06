package com.cleantv.launcher.party

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

private val Billing = listOf("Superstar", "Leading star", "Best friend", "Comic relief", "Item number", "Henchman", "Junior artist", "Background dancer")

/**
 * The player leaderboard, as a film chart. Scores change during games, when this isn't on screen, so it opens on the
 * chart as last seen, then plays the changes: characters climb past each other and their takings count up.
 *
 * @param seen the scores last shown, kept by the caller because this leaves the screen during games.
 */
@Composable
internal fun BoxOffice(state: PartyState, seen: Map<String, Double>, onSeen: (Map<String, Double>) -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf(seen) }
    val scores = state.players.associate { it.id to (state.playerScores[it.id] ?: 0.0) }
    LaunchedEffect(scores) {
        if (scores.any { (id, score) -> (shown[id] ?: 0.0) != score }) delay(CHART_PAUSE_MS)
        shown = scores
        onSeen(scores)
    }
    val ranked = state.players.sortedWith(compareByDescending<Player> { shown[it.id] ?: 0.0 }.thenBy { it.name })
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(1_700, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    Column(modifier.width(236.dp).fillMaxHeight().background(Color(0xF51E0F2B), RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("🎬  BOX OFFICE", color = Marigold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        if (ranked.isEmpty()) {
            Text("Nothing released yet. Scan the code to star in this.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // A bigger party gets thinner rows, down to the name and score alone.
            val rowHeight = (maxHeight / ranked.size.coerceAtLeast(1)).coerceIn(17.dp, 28.dp)
            val fits = floor(maxHeight / rowHeight).toInt().coerceAtLeast(1)
            ranked.forEachIndexed { rank, player ->
                key(player.id) {
                    if (rank < fits) ChartRow(player, rank, ranked.size, shown[player.id] ?: 0.0, rowHeight, bob)
                }
            }
        }
    }
}

@Composable
private fun ChartRow(player: Player, rank: Int, total: Int, score: Double, height: Dp, bob: State<Float>) {
    val y by animateDpAsState(height * rank, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow), label = "climb")
    val takings by animateFloatAsState(score.toFloat(), tween(1_200), label = "takings")
    val leader = rank == 0 && score > 0
    Row(
        Modifier
            .offset(y = y)
            .fillMaxWidth()
            .height(height - 2.dp)
            .background(if (leader) Marigold.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${rank + 1}", color = if (leader) Marigold else Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(12.dp))
        Box(Modifier.width(26.dp), Alignment.Center) {
            // Moved on the layer alone, so bouncing never redraws the row.
            Text(
                player.character, fontSize = if (height >= 25.dp) 16.sp else 12.sp,
                modifier = Modifier.graphicsLayer {
                    val beat = sin(bob.value + player.id.hashCode() % 7)
                    translationY = beat * (if (leader) 3.5f else 2f) * density
                    rotationZ = beat * (if (leader) 9f else 5f)
                },
            )
        }
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f)) {
            Text((if (leader) "👑 " else "") + player.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
            if (height >= 25.dp) {
            Text(
                if (score > 0) billing(rank, total) else "Yet to release",
                color = TeamColors[player.team], fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, lineHeight = 10.sp,
            )
            }
        }
        Text(points(round(takings * 4.0) / 4.0), color = if (leader) Marigold else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Last place is always the background dancer, however few are playing. */
private fun billing(rank: Int, total: Int) = if (rank == total - 1 && total > 1) Billing.last() else Billing[rank.coerceAtMost(Billing.lastIndex)]

private const val CHART_PAUSE_MS = 900L

package com.cleantv.launcher.party

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private const val CLUES = "Clues help, but each one takes a quarter off."
private const val BUZZ = "Know it? Hit BUZZ on your phone. First finger answers out loud, and the host judges."
private const val WRONG = "Wrong answer? You sit that card out, and everyone else gets another go."
private const val POINTS = "Easy 1 · Medium 2 · Hard 3 points, less a quarter per clue. Verdict every 10 cards."

/** What each game is and how it scores, shown before its first round and from the Rules button. */
enum class GameRules(val title: String, val tagline: String, val steps: List<String>, val scoring: String, val players: String = "2 to 16 players") {
    Emoji(
        "Emoji Ka Tamasha", "A whole film. Three tiny pictures. No shame.",
        listOf("The ticket spells out a film in emojis.", BUZZ, WRONG, CLUES), POINTS,
    ),
    Dialogues(
        "Dialogue-Baazi", "You've quoted these for years. Now say where from.",
        listOf("A famous line appears on screen. Dramatic delivery is encouraged.", BUZZ, WRONG, CLUES), POINTS,
    ),
    Cast(
        "Starring?", "The poster has everything except the film's name.",
        listOf("The poster lists the stars. You name the film.", BUZZ, WRONG, CLUES), POINTS,
    ),
    Plots(
        "Plot Ka Satyanash", "Great cinema, described terribly.",
        listOf("A film's plot, explained as badly as possible.", BUZZ, WRONG, "Stuck? Clues help, but each one takes a quarter off. The answer comes with a free insult."), POINTS,
    ),
    Frame(
        "Focus Kar, Bhai", "Squint now, regret later.",
        listOf("A film image starts as a blur and slowly sharpens.", "Your phone shows four films. Tap one. You only get one tap.", "Everyone answers for themselves, so nobody blocks anybody."),
        "Worth 1 point at first, dropping to 0.25 as it sharpens. A wrong tap gets nothing.",
    ),
    Bluff(
        "Sach Ya Bakwaas", "The best liar in the room wins. Your parents would be proud.",
        listOf("A true film fact appears with a blank in it.", "Type a believable lie on your phone to fill the blank. You get 60 seconds.", "All the lies and the truth go up together. Vote for the one you think is real. You get 30 seconds."),
        "+1 for spotting the truth, and +1 for each rival your lie fools.",
        "3 to 12 players",
    ),
    Charades(
        "Bol Bachchan Mana Hai", "One actor. Zero words. Total chaos.",
        listOf("One player gets a film on their phone. Keep it hidden!", "When the team taps I'm Ready, 90 seconds start.", "Act it out without a sound while your team shouts guesses.", "Teams take turns, and everyone gets a go."),
        "+1 to the actor and their team when the host presses Guessed it.",
        "4 to 16 players, at least 2 on each team",
    ),
    Hum(
        "Hmm Tum", "Your humming is about to be judged by friends.",
        listOf("One player gets a song on their phone. Keep it hidden!", "When the team taps I'm Ready, 60 seconds start, and any party music pauses.", "Hum it. No words, no lyrics, no pointing at Shah Rukh.", "Teams take turns, and everyone gets a go."),
        "+1 to the hummer and their team when the host presses Guessed it.",
        "4 to 16 players, at least 2 on each team",
    ),
    Tambola(
        "Housie Full", "Numbers, rhymes and aunties' favourite sport.",
        listOf("Everyone gets a ticket on their phone.", "Numbers are called out with their old rhymes. Tap them on your ticket.", "Got a pattern? Claim it on your phone before anyone else does.", "A claim that isn't complete is a Bogey, and the whole room will know."),
        "Early Five and each line +1. Full House +3.",
    ),
}

/** The rules for whichever game is on. */
internal val PartyState.rules: GameRules
    get() = when (game) {
        Game.Frame -> GameRules.Frame
        Game.Bluff -> GameRules.Bluff
        Game.Tambola -> GameRules.Tambola
        Game.Charades -> if (humming) GameRules.Hum else GameRules.Charades
        else -> when (mode) {
            GuessMode.Movies -> GameRules.Emoji
            GuessMode.Dialogues -> GameRules.Dialogues
            GuessMode.Cast -> GameRules.Cast
            GuessMode.Plots -> GameRules.Plots
        }
    }

/** Over a game that's already running, from the Rules button. */
@Composable
internal fun RulesCard(rules: GameRules, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) { RulesBody(rules, null, onClose) }
    }
}

/** The game's own page before its first round: nothing is playing yet, and the phones show the same rules. */
@Composable
internal fun RulesPage(rules: GameRules, onStart: () -> Unit, onBack: () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(tween(350)) + slideInVertically(tween(450)) { it / 6 }) { RulesBody(rules, onStart, onBack) }
    }
}

@Composable
private fun RulesBody(rules: GameRules, onStart: (() -> Unit)?, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.width(600.dp).background(Paper, CardShape).border(3.dp, Marigold, CardShape)) {
        Column(
            Modifier.fillMaxWidth().background(Plum, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("HOW TO PLAY", color = Paper.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(rules.title, color = Marigold, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("👥  ${rules.players}", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
            Text(
                rules.tagline, color = Plum, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            rules.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).background(Plum, CircleShape), Alignment.Center) {
                        Text("${i + 1}", color = Marigold, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, color = Plum, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "🏆  ${rules.scoring}", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(Marigold.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onStart != null) {
                    RuleButton("▶  Let's play", Modifier.weight(2f).focusRequester(first), onStart)
                    RuleButton("Back", Modifier.weight(1f), onClose)
                } else {
                    RuleButton("Got it", Modifier.weight(1f).focusRequester(first), onClose)
                }
            }
        }
    }
}

@Composable
private fun RuleButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Plum.copy(alpha = 0.12f),
            focusedContainerColor = Plum,
            contentColor = Plum,
            focusedContentColor = Marigold,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(label, Modifier.align(Alignment.Center), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

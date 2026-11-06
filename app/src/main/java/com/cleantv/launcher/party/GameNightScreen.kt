package com.cleantv.launcher.party

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.imageLoader
import coil.request.ImageRequest
import com.cleantv.launcher.CleanTvApplication
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlin.random.Random

internal val TeamColors = listOf(Color(0xFFFF8A3D), Color(0xFFC86BFF))
private val Gold = Color(0xFFFFC94D)

/** Game night on the TV: lobby, scoreboard and the games, run from the remote. */
@Composable
fun GameNightScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val graph = context.applicationContext as CleanTvApplication
    val party = graph.party
    val state by party.state.collectAsStateWithLifecycle()
    val partyOn by graph.partyMode.on.collectAsStateWithLifecycle()
    val server = remember { PartyServer(context, party) }
    // Back from a game lands on that game's tile, not the first one.
    var lastTile by remember { mutableIntStateOf(0) }
    val url = remember { server.url() }
    // A new setting with each game, starting somewhere different each night.
    var sceneIndex by remember { mutableIntStateOf(Random.nextInt(Backdrop.entries.size)) }
    LaunchedEffect(state.game, state.mode, state.humming) { sceneIndex++ }
    val scene = Backdrop.entries[sceneIndex % Backdrop.entries.size]
    var musicOpen by remember { mutableStateOf(false) }
    var chartSeen by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // Each game explains itself the first time it's opened tonight; the Rules button brings it back.
    val rulesShown = remember { mutableSetOf<GameRules>() }
    var rulesOpen by remember { mutableStateOf(false) }
    val playGame = { game: GameRules, start: () -> Unit ->
        if (rulesShown.add(game)) party.holdForRules()
        start()
    }
    var partyPopup by remember { mutableStateOf<Boolean?>(null) }
    val welcomeHold = remember { if (party.welcomed) 350L else 2_000L.also { party.welcomed = true } }
    val nowPlaying = rememberNowPlaying(graph.music)
    // Nobody can guess a hummed song over a real one.
    val humming = state.game == Game.Charades && state.humming && state.turnStarted && !state.turnOver
    LaunchedEffect(humming) { graph.music.holdForHumming(humming) }

    // The phone page is only reachable while game night is open.
    DisposableEffect(Unit) {
        runCatching { server.start(5_000, false) }
        onDispose { server.stop() }
    }
    BackHandler { if (state.game == Game.Lobby) onExit() else party.backToLobby() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(scene, Modifier.fillMaxSize(), tween(900), label = "backdrop") { GameBackdrop(it, Modifier.fillMaxSize()) }
        // Keeps the title and scores readable over whichever setting is showing.
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))))
        NowPlayingStrip(nowPlaying, Modifier.align(Alignment.BottomCenter))
        if (rulesOpen) RulesCard(state.rules) { rulesOpen = false }
        if (musicOpen) MusicPanel(graph.music, graph.notices::show) { musicOpen = false }
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            TopRow(state, url, partyOn, onRules = { rulesOpen = true }, onMusic = { musicOpen = true }, onPartyMode = { partyPopup = !partyOn; graph.partyMode.set(!partyOn) }, onReset = party::resetScores)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.game) {
                    Game.Lobby -> Lobby(state, url, party, lastTile, { lastTile = it }, chartSeen, { chartSeen = it }, playGame)
                    else -> if (state.loading) {
                        Center("Getting ${state.label} ready…")
                    } else if (state.rulesUp) {
                        RulesPage(state.rules, party::rulesDone, party::backToLobby)
                    } else {
                        when (state.game) {
                            Game.Emoji -> EmojiGame(state, party)
                            Game.Charades -> CharadesGame(state, party)
                            Game.Tambola -> TambolaGame(state, party)
                            Game.Frame -> FrameGame(state, party)
                            Game.Bluff -> BluffGame(state, party)
                            Game.Lobby -> Unit
                        }
                    }
                }
            }
        }
        partyPopup?.let { on -> key(on) { PartyPopup(on, graph.lights.available) { partyPopup = null } } }
        WelcomeCurtain(welcomeHold)
    }
}

@Composable
private fun TopRow(state: PartyState, url: String?, partyOn: Boolean, onRules: () -> Unit, onMusic: () -> Unit, onPartyMode: () -> Unit, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🪔", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            val small = if (state.game == Game.Lobby) "Welcome to ${PartyName.hosts}".trim() else PartyName.hosts
            if (small.isNotEmpty()) {
                Text(small, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp)
            }
            Text(PartyName.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        }
        if (state.game != Game.Lobby) {
            Spacer(Modifier.width(14.dp))
            Text(state.label, color = Gold, fontSize = 16.sp)
        }
        // Takes whatever room is left, so a long game name or score trims this, never the score.
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), Alignment.CenterEnd) {
            if (state.game != Game.Lobby && url != null) {
                Text("Join: $url  ·  ${state.players.size} playing", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (state.game != Game.Lobby) {
            TopButton("📖 Rules", onRules)
            Spacer(Modifier.width(6.dp))
        }
        TopButton("🎵 Music", onMusic)
        if (state.game == Game.Lobby) {
            Spacer(Modifier.width(6.dp))
            TopButton(if (partyOn) "✨ Party on" else "💡 Party mode", onPartyMode)
            Spacer(Modifier.width(6.dp))
            TopButton("↺ Reset", onReset)
        }
        TeamNames.forEachIndexed { i, name ->
            Row(
                Modifier.padding(start = 10.dp).background(TeamColors[i].copy(alpha = 0.22f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = TeamColors[i], fontSize = 14.sp, fontWeight = FontWeight.SemiBold, softWrap = false)
                Spacer(Modifier.width(10.dp))
                Text(points(state.scores[i]), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, softWrap = false)
            }
        }
    }
}

// ---------- Lobby

@Composable
private fun Lobby(
    state: PartyState,
    url: String?,
    party: Party,
    lastTile: Int,
    onTile: (Int) -> Unit,
    chartSeen: Map<String, Double>,
    onChartSeen: (Map<String, Double>) -> Unit,
    play: (GameRules, () -> Unit) -> Unit,
) {
    val tiles = remember { List(9) { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { tiles[lastTile].requestFocus() } }
    fun Modifier.tile(i: Int) = focusRequester(tiles[i]).onFocusChanged { if (it.isFocused) onTile(i) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            JoinCard(url, state.players.size)
            TeamNames.forEachIndexed { i, name -> TeamCard(name, TeamColors[i], state.players.filter { it.team == i }, Modifier.weight(1f)) }
            BoxOffice(state, chartSeen, onChartSeen, Modifier)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameTile("😀🎬", "Emoji Ka Tamasha", Modifier.weight(1f).tile(0)) { play(GameRules.Emoji) { party.startGuessing(GuessMode.Movies) } }
            GameTile("💬", "Dialogue-Baazi", Modifier.weight(1f).tile(1)) { play(GameRules.Dialogues) { party.startGuessing(GuessMode.Dialogues) } }
            GameTile("🌟", "Starring?", Modifier.weight(1f).tile(2)) { play(GameRules.Cast) { party.startGuessing(GuessMode.Cast) } }
            GameTile("🤦", "Plot Ka Satyanash", Modifier.weight(1f).tile(3)) { play(GameRules.Plots) { party.startGuessing(GuessMode.Plots) } }
            GameTile("🖼️", "Focus Kar, Bhai", Modifier.weight(1f).tile(4)) { play(GameRules.Frame) { party.startFrames() } }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameTile("🤥", "Sach Ya Bakwaas", Modifier.weight(1f).tile(5)) { play(GameRules.Bluff) { party.startBluff() } }
            GameTile("🎭", "Bol Bachchan Mana Hai", Modifier.weight(1f).tile(6)) { play(GameRules.Charades) { party.startCharades() } }
            GameTile("🎶", "Hmm Tum", Modifier.weight(1f).tile(7)) { play(GameRules.Hum) { party.startCharades(hum = true) } }
            GameTile("🎟️", "Housie Full", Modifier.weight(1f).tile(8)) { play(GameRules.Tambola) { party.startTambola() } }
        }
    }
}

/** Warm dark plum with a thin gold edge, so the cards belong to the festive wallpaper. */
private fun Modifier.festiveCard(edge: Color = Gold.copy(alpha = 0.45f)) = this
    .background(Color(0xF51E0F2B), LobbyShape)
    .border(1.dp, edge, LobbyShape)

private val LobbyShape = RoundedCornerShape(22.dp)

@Composable
private fun JoinCard(url: String?, playing: Int) {
    Column(
        Modifier.width(196.dp).fillMaxHeight().festiveCard().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Join the game", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Scan with your phone camera", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        val qr = remember(url) { url?.let { qrCode(it, 512) } }
        if (qr != null) {
            Image(qr, "Scan to join", Modifier.size(112.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(8.dp))
        } else {
            Text("Connect the TV to Wi-Fi", color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(url.orEmpty().removePrefix("http://"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            if (playing == 0) "Same Wi-Fi as the TV" else "$playing playing · same Wi-Fi",
            color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1,
        )
    }
}

@Composable
private fun TeamCard(name: String, color: Color, members: List<Player>, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .festiveCard(color.copy(alpha = 0.55f))
            .background(Brush.verticalGradient(0f to color.copy(alpha = 0.22f), 0.35f to Color.Transparent), LobbyShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (members.isNotEmpty()) Text("${members.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        if (members.isEmpty()) {
            Text("Nobody yet", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
        val crowded = members.size > 6
        members.forEach { p ->
            Row(Modifier.padding(bottom = if (crowded) 2.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (crowded) 19.dp else 24.dp).background(color.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(p.character, fontSize = if (crowded) 10.sp else 13.sp)
                }
                Spacer(Modifier.width(7.dp))
                Text(p.name, color = Color.White, fontSize = if (crowded) 11.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GameTile(icon: String, label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(66.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Gold.copy(alpha = 0.35f) else Color(0xCC1E0F2B),
            focusedContainerColor = Gold,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF1E0F2B),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), shape = RoundedCornerShape(18.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

// ---------- Games

/** A host action on a game screen, chosen with the arrows and pressed with OK. [dim]: nothing to do right now. */
@Composable
private fun HostButton(label: String, modifier: Modifier = Modifier, dim: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xCC1E0F2B),
            focusedContainerColor = Gold,
            contentColor = Color.White.copy(alpha = if (dim) 0.4f else 1f),
            focusedContentColor = Color(0xFF1E0F2B),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The top bar's small buttons, so they don't compete with the game. */
@Composable
private fun TopButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xCC1E0F2B),
            focusedContainerColor = Gold,
            contentColor = Color.White,
            focusedContentColor = Color(0xFF1E0F2B),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HostButtons(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), content = content)
}

@Composable
private fun EmojiGame(s: PartyState, party: Party) {
    Deck(s, s.verdict, Modifier.fillMaxSize(), slide = false) { st ->
        if (st.verdict) VerdictScreen(st, party) else GuessScreen(st, party)
    }
}

@Composable
private fun GuessScreen(s: PartyState, party: Party) {
    val p = s.puzzle
    val clue = remember { FocusRequester() }
    val correct = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    // Focus waits on what the host most likely needs next.
    LaunchedEffect(s.puzzleIndex, s.buzzedBy, s.revealed, s.verdict) {
        withFrameNanos { }
        runCatching {
            when {
                p == null -> Unit
                s.revealed -> next.requestFocus()
                s.buzzedBy != null -> correct.requestFocus()
                else -> clue.requestFocus()
            }
        }
    }
    if (p == null) return Center("That's all the puzzles! Back for the lobby.")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Card ${s.puzzleIndex % Party.VERDICT_EVERY + 1} of ${Party.VERDICT_EVERY}",
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Deck(s, s.puzzleIndex, Modifier.weight(1f).width(640.dp)) { st ->
            st.puzzle?.let { GuessCard(st, it, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(6.dp))
        val who = s.player(s.buzzedBy)
        Box(Modifier.height(36.dp), Alignment.Center) {
            when {
                s.revealed -> Unit
                who != null -> PaperStrip("🔔  ${who.name}  ·  ${TeamNames[who.team]}", Color(0xFFB3261E))
                else -> PaperStrip("🔔  Buzz on your phone!")
            }
        }
        Spacer(Modifier.height(6.dp))
        HostButtons {
            HostButton("🎵 Song", Modifier.focusRequester(clue), dim = p.song.isEmpty() || Hint.Song in s.hints || s.revealed) {
                if (p.song.isNotEmpty()) party.emojiHint(Hint.Song)
            }
            HostButton("🔤 Title", dim = Hint.Title in s.hints || s.revealed) { party.emojiHint(Hint.Title) }
            HostButton("💡 Story", dim = p.story.isEmpty() || Hint.Story in s.hints || s.revealed) {
                if (p.story.isNotEmpty()) party.emojiHint(Hint.Story)
            }
            HostButton("✓ Correct", Modifier.focusRequester(correct), dim = s.buzzedBy == null || s.revealed) { party.emojiCorrect() }
            HostButton("✗ Wrong", dim = s.buzzedBy == null || s.revealed) { party.emojiWrong() }
            HostButton("Answer", dim = s.revealed) { party.emojiReveal() }
            HostButton("Next ▶", Modifier.focusRequester(next)) { party.emojiNext() }
        }
    }
}

/** The end of ten cards: who won them, and a review they didn't ask for. */
@Composable
private fun VerdictScreen(s: PartyState, party: Party) {
    val keepGoing = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { keepGoing.requestFocus() }
    }
    val gained = s.scores.zip(s.roundStartScores) { now, start -> (now - start).coerceAtLeast(0.0) }
    val winner = if (gained[0] == gained[1]) null else if (gained[0] > gained[1]) 0 else 1
    val margin = kotlin.math.abs(gained[0] - gained[1])
    val (rating, review) = when {
        gained.sum() == 0.0 -> "DISASTER" to "Nobody got anything. Even the producer left at the interval."
        winner == null -> "IT'S A TIE" to "Climax pending. The sequel has already been announced."
        margin >= 6 -> "BLOCKBUSTER" to "Housefull for 25 weeks. ${TeamNames[1 - winner]}: straight to Set Max at 3 pm."
        margin >= 2 -> "SUPER HIT" to "Solid collections. ${TeamNames[1 - winner]} blames the second half."
        else -> "PHOTO FINISH" to "Won by a single dialogue. ${TeamNames[1 - winner]} demands a recount."
    }
    val keeda = s.roundWins.maxByOrNull { it.value }?.let { (id, wins) -> s.player(id)?.let { it.name to wins } }
    val hasMore = s.puzzleIndex < s.puzzles.size
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.width(620.dp).background(Paper, CardShape).border(3.dp, Marigold, CardShape).clip(CardShape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().background(Plum).padding(vertical = 9.dp), Alignment.Center) {
                Text("🪔  FINAL VERDICT", color = Marigold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(rating, color = Color(0xFFB3261E), fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                review, color = Plum, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                TeamNames.forEachIndexed { i, name ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${points(gained[i])}", color = Plum, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(name, color = Plum.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            keeda?.let { (name, wins) ->
                Text("Filmi keeda of the round: $name ($wins)", color = Plum, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.weight(1f))
        HostButtons {
            if (hasMore) {
                HostButton("Keep going ▶", Modifier.focusRequester(keepGoing)) { party.verdictContinue() }
                HostButton("Back to lobby") { party.backToLobby() }
            } else {
                HostButton("Back to lobby", Modifier.focusRequester(keepGoing)) { party.backToLobby() }
            }
        }
    }
}

@Composable
private fun CharadesGame(s: PartyState, party: Party) {
    val main = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    LaunchedEffect(s.titleIndex, s.turnOver) {
        withFrameNanos { }
        runCatching { if (s.turnOver) next.requestFocus() else main.requestFocus() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.titleIndex) {
        while (true) {
            now = System.currentTimeMillis()
            // This loop outlives the state it started with, so read the current one.
            party.state.value.let { if (it.turnStarted && !it.turnOver && now >= it.turnEndsAt) party.charadesPass() }
            delay(250)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round ${s.titleIndex + 1}", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Deck(s, s.titleIndex, Modifier.weight(1f).width(680.dp)) { s ->
            val actor = s.player(s.actorId)
            val secondsLeft = ((s.turnEndsAt - now) / 1000).coerceAtLeast(0)
            FlipCard(
                s.turnOver, Modifier.fillMaxSize(),
                front = {
                    Marquee((now / 450).toInt(), Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (s.humming) "♪  NOW HUMMING  ♪" else "★  NOW SHOWING  ★", color = Marigold, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(actor?.name ?: "Nobody on this team yet", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
                            Text(TeamNames[s.actingTeam], color = TeamColors[s.actingTeam], fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            if (s.turnStarted) {
                                CountdownRing(secondsLeft, (if (s.humming) Party.HUM_MS else Party.CHARADES_MS) / 1000, 124.dp)
                            } else {
                                PaperStrip(if (actor != null) "Tap \"I'm ready\" on a ${TeamNames[s.actingTeam]} phone" else "Join from a phone")
                            }
                        }
                    }
                },
                back = {
                    Column(
                        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF6A2A7E), Color(0xFF250A33))), CardShape).border(3.dp, Marigold, CardShape).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (s.humming) "THE SONG WAS" else "THE FILM WAS", color = Marigold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(s.title.orEmpty(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        HostButtons {
            // One button that changes, not two: swapping buttons would drop focus onto Pass when a phone starts the clock.
            HostButton(
                if (s.turnStarted) "✓ Guessed it (+1)" else "▶ Start timer",
                Modifier.focusRequester(main),
                dim = s.turnOver,
            ) { if (s.turnStarted) party.charadesGuessed() else party.charadesStart() }
            HostButton("Pass", dim = s.turnOver) { party.charadesPass() }
            HostButton("Next team ▶", Modifier.focusRequester(next)) { party.charadesNext() }
        }
    }
}

/** A hidden image costs no blur effect: a tiny copy stretched smooth looks the same and is cheap on the TV. */
private fun shrunk(full: Bitmap, width: Int): Bitmap {
    var b = full
    // Halving in steps keeps it smooth; one big jump would keep stray pixels and look noisy.
    while (b.width / 2 >= width) b = Bitmap.createScaledBitmap(b, b.width / 2, (b.height / 2).coerceAtLeast(1), true)
    return if (b.width == width) b else Bitmap.createScaledBitmap(b, width, (width * full.height / full.width).coerceAtLeast(1), true)
}

private val FrameWidths = listOf(16, 24, 36, 54, 80, 120, 200)

/** The picture in its film strip: fetches it once, then shows it as sharp as the round has reached. */
@Composable
private fun FrameStrip(s: PartyState, party: Party) {
    val frame = s.frame
    val context = LocalContext.current
    var full by remember(frame) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(frame) {
        frame ?: return@LaunchedEffect
        val drawable = context.imageLoader.execute(ImageRequest.Builder(context).data(frame.url).allowHardware(false).build()).drawable
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        // A film whose image won't load is skipped rather than holding up the party.
        if (bitmap == null) party.frameNext() else { full = bitmap; party.frameShown() }
        s.frames.getOrNull(s.frameIndex + 1)?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it.url).build()) }
    }
    val shown = remember(full, s.frameLevel, s.revealed) {
        full?.let { (if (s.revealed || s.frameLevel >= FrameWidths.size) it else shrunk(it, FrameWidths[s.frameLevel])).asImageBitmap() }
    }
    FilmStrip(Modifier.fillMaxSize()) {
        if (shown != null) {
            Image(shown, null, Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Threading the projector…", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp) }
        }
    }
}

@Composable
private fun FrameGame(s: PartyState, party: Party) {
    val frame = s.frame
    val answer = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    LaunchedEffect(s.frameIndex, s.revealed) {
        withFrameNanos { }
        runCatching {
            when {
                frame == null -> Unit
                s.revealed -> next.requestFocus()
                else -> answer.requestFocus()
            }
        }
    }
    if (frame == null) return Center(if (s.frames.isEmpty()) "Couldn't get the film images. Check the internet, then try again." else "That's all the frames! Back for the lobby.")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Frame ${s.frameIndex + 1} of ${s.frames.size}" +
                if (s.revealed) "" else "  ·  worth ${points(s.frameWorth)}  ·  ${s.frameAnswers.size} of ${s.players.size} answered",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Deck(s, s.frameIndex, Modifier.weight(1f).aspectRatio(16f / 8f)) { st -> FrameStrip(st, party) }
        Spacer(Modifier.height(8.dp))
        // A fixed height, so the picture doesn't change size at the reveal.
        Column(Modifier.height(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (s.revealed) {
                Text(frame.film + (frame.year?.let { "  ($it)" } ?: ""), color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                val winners = s.frameAnswers.filterValues { it.choice == frame.right }.entries.sortedByDescending { it.value.worth }
                    .mapNotNull { (id, answer) -> s.player(id)?.let { "${it.name} +${points(answer.worth)}" } }
                Text(
                    if (winners.isEmpty()) "Nobody got it" else winners.joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                PaperStrip("🎞️  Pick the film on your phone!")
            }
        }
        Spacer(Modifier.height(10.dp))
        HostButtons {
            HostButton("Show answer", Modifier.focusRequester(answer), dim = s.revealed) { party.frameReveal() }
            HostButton("Next ▶", Modifier.focusRequester(next)) { party.frameNext() }
        }
    }
}

@Composable
private fun BluffGame(s: PartyState, party: Party) {
    val bluff = s.bluff
    val main = remember { FocusRequester() }
    LaunchedEffect(s.bluffIndex, s.bluffPhase) {
        withFrameNanos { }
        runCatching { main.requestFocus() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // This loop outlives the state it started with, so read the current one.
            party.state.value.let {
                if (now >= it.phaseEndsAt) when (it.bluffPhase) {
                    BluffPhase.Writing -> party.bluffVoting()
                    BluffPhase.Voting -> party.bluffReveal()
                    BluffPhase.Reveal -> Unit
                }
            }
            delay(250)
        }
    }
    if (bluff == null) return Center("That's all the questions! Back for the lobby.")
    val secondsLeft = ((s.phaseEndsAt - now) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when (s.bluffPhase) {
                BluffPhase.Writing -> "Round ${s.bluffIndex + 1}  ·  Write a believable lie on your phone  ·  ${s.fakes.size} of ${s.players.size} done"
                BluffPhase.Voting -> "Round ${s.bluffIndex + 1}  ·  Which one is true? Vote on your phone  ·  ${s.votes.size} of ${s.players.size} voted"
                BluffPhase.Reveal -> "Round ${s.bluffIndex + 1}  ·  +1 for spotting the truth, +1 for each rival you fooled"
            },
            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Deck(s, s.bluffIndex, Modifier.weight(1f).fillMaxWidth()) { s ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.fillMaxWidth().background(Paper, CardShape).border(3.dp, Marigold, CardShape).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🤥  TRUE STORY. PROBABLY.", color = Color(0xFFB3261E), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(s.bluff?.prompt.orEmpty(), color = Plum, fontSize = 23.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            if (s.bluffPhase == BluffPhase.Writing) {
                Spacer(Modifier.weight(1f))
                CountdownRing(secondsLeft, Party.BLUFF_WRITE_MS / 1000, 190.dp)
                Spacer(Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    val columns = if (s.bluffOptions.size > 9) 3 else 2
                s.bluffOptions.withIndex().toList().chunked(columns).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { (i, option) -> BluffOptionCard(s, i, option, Modifier.weight(1f)) }
                            repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (s.bluffPhase == BluffPhase.Voting) {
                        Box(Modifier.align(Alignment.CenterHorizontally)) { PaperStrip("⏱  $secondsLeft s to vote") }
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        HostButtons {
            HostButton(
                when (s.bluffPhase) {
                    BluffPhase.Writing -> "Start voting ▶"
                    BluffPhase.Voting -> "Show the truth ▶"
                    BluffPhase.Reveal -> "Next question ▶"
                },
                Modifier.focusRequester(main),
            ) {
                when (s.bluffPhase) {
                    BluffPhase.Writing -> party.bluffVoting()
                    BluffPhase.Voting -> party.bluffReveal()
                    BluffPhase.Reveal -> party.bluffNext()
                }
            }
            HostButton("Skip question", dim = s.bluffPhase == BluffPhase.Reveal) { if (s.bluffPhase != BluffPhase.Reveal) party.bluffNext() }
        }
    }
}

@Composable
private fun BluffOptionCard(s: PartyState, index: Int, option: BluffOption, modifier: Modifier) {
    val reveal = s.bluffPhase == BluffPhase.Reveal
    val right = reveal && option.truth
    val voters = s.votes.filterValues { it == index }.keys.mapNotNull { s.player(it)?.name }
    // A full room's answers have to fit five rows, even under a two-line question.
    val compact = s.bluffOptions.size > 6
    val crowded = s.bluffOptions.size > 9
    Row(
        modifier
            .background(if (right) Color(0xFF2E7D32) else Paper, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = if (compact) 3.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (right) Color.White else Plum
        Column(Modifier.weight(1f)) {
            Text("${"ABCDEFGHIJKL"[index]}.  ${option.text}", color = ink, fontSize = if (crowded) 13.sp else if (compact) 16.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (reveal) {
                val who = if (option.truth) "The real answer" else s.player(option.author)?.let { "${it.name}'s lie" } ?: "Our lie"
                Text(
                    who + if (voters.isEmpty()) "" else (if (option.truth) "  ·  spotted by " else "  ·  fooled ") + voters.joinToString(", "),
                    color = ink.copy(alpha = 0.8f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (reveal) Stamp(if (option.truth) "TRUE" else "LIE", if (option.truth) Color.White else Color(0xFFB3261E))
    }
}

@Composable
private fun TambolaGame(s: PartyState, party: Party) {
    val call = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { call.requestFocus() } }
    val last = s.called.lastOrNull()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            // The number on a lottery ball, the recent ones lined up behind it.
            Box(
                Modifier.size(190.dp).background(Brush.radialGradient(listOf(Color(0xFFFFF0B0), Marigold, Color(0xFFB9720A))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(128.dp).background(Paper, CircleShape), contentAlignment = Alignment.Center) {
                    Text(last?.toString() ?: "?", color = Plum, fontSize = 72.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            PaperStrip(last?.let { Tambola.rhyme(it) } ?: "Press Call next for the first number")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.called.takeLast(6).dropLast(1).reversed().forEach { n ->
                    Box(Modifier.size(34.dp).background(Paper.copy(alpha = 0.85f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("$n", color = Plum, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            s.banner?.let { Spacer(Modifier.height(10.dp)); PaperStrip(it, Color(0xFFB3261E)) }
            Spacer(Modifier.weight(1f))
            HostButtons {
                HostButton("Call next", Modifier.focusRequester(call)) { party.callNumber() }
                HostButton("Auto-call: ${if (s.autoCall) "on" else "off"}") { party.toggleAutoCall() }
            }
            Spacer(Modifier.height(6.dp))
            Text("${s.called.size} of 90 called", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Column(Modifier.weight(0.58f).padding(start = 24.dp)) {
            // The 1–90 board.
            val called = s.called.toSet()
            for (row in 0 until 9) {
                Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 1..10) {
                        val n = row * 10 + col
                        Box(
                            Modifier.weight(1f).height(34.dp).background(
                                when {
                                    n == last -> Marigold
                                    n in called -> Paper
                                    else -> Color.Black.copy(alpha = 0.35f)
                                },
                                RoundedCornerShape(6.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", color = if (n in called) Color(0xFF2A1440) else Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Claim.entries.forEach { c ->
                    val winner = s.player(s.claims[c])
                    Column(Modifier.weight(1f).background(if (winner != null) Marigold else Paper.copy(alpha = 0.9f), RoundedCornerShape(10.dp)).padding(8.dp)) {
                        Text(c.label, color = Plum, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(winner?.name ?: "still open", color = Plum.copy(alpha = if (winner != null) 1f else 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---------- bits

@Composable
private fun Banner(text: String, sub: String, color: Color) {
    Column(
        Modifier.background(color.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = color, fontSize = 18.sp)
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 24.sp) }
}

/** Scores can be fractions now that clues cost points: "2", "1.75". */
internal fun points(p: Double) = if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()

private fun qrCode(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

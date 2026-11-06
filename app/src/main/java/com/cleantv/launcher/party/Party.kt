package com.cleantv.launcher.party

import com.cleantv.launcher.BuildConfig
import com.cleantv.launcher.data.jarvis.JarvisVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Game(val label: String) { Lobby("Lobby"), Emoji("Emoji Ka Tamasha"), Charades("Bol Bachchan Mana Hai"), Tambola("Housie Full"), Frame("Focus Kar, Bhai"), Bluff("Sach Ya Bakwaas") }

/** The clues the host can show on an emoji puzzle, each on its own. */
enum class Hint { Song, Title, Story }

enum class BluffPhase { Writing, Voting, Reveal }

/** One answer to vote on. [author] is the player who wrote it; null is the truth or one of our own lies. */
data class BluffOption(val text: String, val author: String?, val truth: Boolean = false)

/** A locked-in Guess the Frame choice and what it was worth when tapped. */
data class FrameAnswer(val choice: Int, val worth: Double)

data class Player(val id: String, val name: String, val team: Int, val character: String)

/** Who a player is on the leaderboard. All older emojis, so every phone and the TV can draw them. */
private val Characters = listOf("🦁", "🐯", "🐼", "🦊", "🐸", "🐵", "🐧", "🦄", "🐙", "🦉", "🐨", "🐰", "🦚", "🐘", "🐻", "🐮")

val TeamNames = BuildConfig.PARTY_TEAMS.split(',').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size == 2 } ?: listOf("Team One", "Team Two")

/** What the party is called, from the `party.*` settings. */
object PartyName {
    val title: String = BuildConfig.PARTY_TITLE.ifEmpty { "Game Night" }

    /** "Sam & Alex's", or empty. */
    val hosts: String = BuildConfig.PARTY_HOSTS
    val full: String = if (hosts.isEmpty()) title else "$hosts $title"
}

/** A Tambola ticket: 3 rows × 9 columns, 5 numbers per row; null is a blank square. */
typealias Ticket = List<List<Int?>>

enum class Claim(val label: String) { EarlyFive("Early Five"), TopLine("Top Line"), MiddleLine("Middle Line"), BottomLine("Bottom Line"), FullHouse("Full House") }

data class PartyState(
    val players: List<Player> = emptyList(),
    val scores: List<Double> = listOf(0.0, 0.0),
    val playerScores: Map<String, Double> = emptyMap(),
    val rulesUp: Boolean = false, // the game's rules are showing; nothing has started yet
    val game: Game = Game.Lobby,
    val loading: Boolean = false,
    val banner: String? = null, // a short message on the TV, e.g. "Bogey! Ravi's claim was wrong"
    // Buzzer (Emoji Bollywood)
    val buzzOpen: Boolean = false,
    val buzzedBy: String? = null,
    val lockedOut: Set<String> = emptySet(),
    // Emoji Bollywood and the other buzzer games
    val mode: GuessMode = GuessMode.Movies,
    val puzzles: List<EmojiPuzzle> = emptyList(),
    val puzzleIndex: Int = 0,
    val revealed: Boolean = false,
    val hints: Set<Hint> = emptySet(),
    val awarded: Double? = null, // what the right answer earned, once given
    // A Final Verdict every [Party.VERDICT_EVERY] puzzles
    val verdict: Boolean = false,
    val roundStartScores: List<Double> = listOf(0.0, 0.0),
    val roundWins: Map<String, Int> = emptyMap(), // player id → right answers this round
    // Dumb charades
    val titles: List<String> = emptyList(),
    val titleIndex: Int = 0,
    val actorId: String? = null,
    val actingTeam: Int = 0,
    val turnEndsAt: Long = 0,
    val turnStarted: Boolean = false,
    val turnOver: Boolean = false,
    val humming: Boolean = false, // Hum It: charades with songs
    // Guess the Frame
    val frames: List<Frame> = emptyList(),
    val frameIndex: Int = 0,
    val frameLevel: Int = 0,
    val frameOpen: Boolean = false,
    val frameAnswers: Map<String, FrameAnswer> = emptyMap(),
    // Filmi Bluff
    val bluffs: List<Bluff> = emptyList(),
    val bluffIndex: Int = 0,
    val bluffPhase: BluffPhase = BluffPhase.Writing,
    val fakes: Map<String, String> = emptyMap(), // player id → their lie
    val bluffOptions: List<BluffOption> = emptyList(),
    val votes: Map<String, Int> = emptyMap(), // player id → option index
    val phaseEndsAt: Long = 0,
    // Tambola
    val tickets: Map<String, Ticket> = emptyMap(),
    val called: List<Int> = emptyList(),
    val claims: Map<Claim, String> = emptyMap(), // claim → player id
    val autoCall: Boolean = false,
) {
    fun player(id: String?) = players.firstOrNull { it.id == id }
    val puzzle get() = puzzles.getOrNull(puzzleIndex)

    /** What a right answer earns now: the puzzle's points by difficulty, less a quarter of them for each clue shown. */
    val puzzleWorth get() = (puzzle?.basePoints ?: 1.0) * (1.0 - 0.25 * hints.size).coerceAtLeast(0.25)
    val frame get() = frames.getOrNull(frameIndex)

    /** Guess the Frame: the earlier the answer, the more it's worth. */
    val frameWorth get() = (1.0 - 0.25 * (frameLevel / 2)).coerceAtLeast(0.25)
    val bluff get() = bluffs.getOrNull(bluffIndex)
    val label get() = when {
        game == Game.Emoji -> mode.label
        game == Game.Charades && humming -> "Hmm Tum"
        else -> game.label
    }
    val title get() = titles.getOrNull(titleIndex)
}

/**
 * Game night. The TV drives the games with the remote; phones join over the home Wi-Fi ([PartyServer]) to
 * buzz, see the charades title, write lies and hold Tambola tickets. All state lives here; both sides read it.
 */
class Party(private val content: GameContent, private val voice: JarvisVoice, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PartyState())
    val state: StateFlow<PartyState> = _state
    private var autoCallJob: Job? = null
    private var frameJob: Job? = null
    private var heldAnnouncement: String? = null

    /** The long welcome is for the night's first arrival. */
    var welcomed = false

    // ---------- players

    @Synchronized
    fun join(name: String, team: Int?): Player {
        val cleanName = name.trim().take(20).ifEmpty { "Guest" }
        val s = _state.value
        // Unpicked team: whichever is smaller.
        val t = team?.coerceIn(0, 1) ?: (0..1).minBy { i -> s.players.count { it.team == i } }
        // Everyone different, until there are more players than characters.
        val free = Characters - s.players.map { it.character }.toSet()
        val player = Player(UUID.randomUUID().toString().take(8), cleanName, t, free.ifEmpty { Characters }.random())
        _state.update {
            val tickets = if (it.game == Game.Tambola) it.tickets + (player.id to Tambola.ticket()) else it.tickets
            it.copy(players = it.players + player, tickets = tickets)
        }
        return player
    }

    fun resetScores() = _state.update { it.copy(scores = listOf(0.0, 0.0), playerScores = emptyMap()) }

    /** To the team's score and the player's own, for the leaderboard. */
    private fun addPoints(player: Player, points: Double = 1.0) = _state.update { s ->
        s.copy(
            scores = s.scores.mapIndexed { i, v -> if (i == player.team) v + points else v },
            playerScores = s.playerScores + (player.id to (s.playerScores[player.id] ?: 0.0) + points),
        )
    }

    fun backToLobby() {
        autoCallJob?.cancel()
        frameJob?.cancel()
        heldAnnouncement = null
        _state.update { it.copy(rulesUp = false, game = Game.Lobby, buzzOpen = false, buzzedBy = null, banner = null, autoCall = false) }
    }

    // ---------- rules

    /** Call before starting a game: it opens on its rules, with phones showing them and nothing live. */
    fun holdForRules() = _state.update { it.copy(rulesUp = true) }

    /** Everyone's read the rules: the game really starts, with its full clock and its spoken opening. */
    fun rulesDone() {
        _state.update { s ->
            s.copy(rulesUp = false, phaseEndsAt = if (s.game == Game.Bluff) now() + BLUFF_WRITE_MS else s.phaseEndsAt)
        }
        heldAnnouncement?.let { voice.say(it) }
        heldAnnouncement = null
    }

    private fun announce(text: String) {
        if (_state.value.rulesUp) heldAnnouncement = text else voice.say(text)
    }

    // ---------- Emoji Bollywood (buzzers)

    fun startGuessing(mode: GuessMode) = load(Game.Emoji) {
        val puzzles = content.puzzles(mode)
        _state.update { it.copy(mode = mode, puzzles = puzzles, puzzleIndex = 0, revealed = false, hints = emptySet(), awarded = null, buzzOpen = true, buzzedBy = null, lockedOut = emptySet(), verdict = false, roundStartScores = it.scores, roundWins = emptyMap()) }
    }

    @Synchronized
    fun buzz(playerId: String): Boolean {
        val s = _state.value
        if (s.rulesUp || !s.buzzOpen || s.buzzedBy != null || playerId in s.lockedOut || s.player(playerId) == null) return false
        _state.update { it.copy(buzzedBy = playerId) }
        voice.say(s.player(playerId)!!.name)
        return true
    }

    /** The buzzer's answer was right: point to their team, show the answer. */
    fun emojiCorrect() {
        val s = _state.value
        val p = s.player(s.buzzedBy) ?: return
        if (s.revealed) return // a second press mustn't score twice
        addPoints(p, s.puzzleWorth)
        _state.update { it.copy(revealed = true, buzzOpen = false, awarded = s.puzzleWorth, roundWins = it.roundWins + (p.id to (it.roundWins[p.id] ?: 0) + 1)) }
    }

    /** Wrong: that player sits this one out, buzzers reopen for everyone else. */
    fun emojiWrong() = _state.update { s ->
        if (s.buzzedBy == null || s.revealed) s else s.copy(lockedOut = s.lockedOut + s.buzzedBy, buzzedBy = null)
    }

    fun emojiHint(hint: Hint) = _state.update { it.copy(hints = it.hints + hint) }

    fun emojiReveal() = _state.update { it.copy(revealed = true, buzzOpen = false) }

    fun emojiNext() {
        _state.value.let { s -> s.puzzle?.let { content.markPlayed(s.mode, it) } }
        _state.update { s ->
            val index = (s.puzzleIndex + 1).coerceAtMost(s.puzzles.size)
            val roundOver = index % VERDICT_EVERY == 0 || index == s.puzzles.size
            s.copy(puzzleIndex = index, revealed = false, hints = emptySet(), awarded = null, buzzOpen = !roundOver, buzzedBy = null, lockedOut = emptySet(), verdict = roundOver)
        }
    }

    /** Past the Final Verdict and into the next round's plots. */
    fun verdictContinue() = _state.update { it.copy(verdict = false, buzzOpen = true, roundStartScores = it.scores, roundWins = emptyMap()) }

    // ---------- Dumb charades

    fun startCharades(hum: Boolean = false) = load(Game.Charades) {
        val titles = if (hum) content.humTitles() else content.charadesTitles()
        _state.update { it.copy(titles = titles, titleIndex = 0, actingTeam = 0, humming = hum) }
        newCharadesTurn(team = 0)
    }

    /** Picks the next actor from [team], taking turns within the team, and starts the clock. */
    private fun newCharadesTurn(team: Int) {
        val s = _state.value
        val members = s.players.filter { it.team == team }
        val turnsTaken = s.titleIndex / 2
        val actor = members.getOrNull(turnsTaken % members.size.coerceAtLeast(1))
        _state.update { it.copy(actingTeam = team, actorId = actor?.id, turnEndsAt = 0, turnStarted = false, turnOver = false) }
        val job = if (s.humming) "humming" else "acting"
        actor?.let { announce("${it.name}, you're $job. Check your phone. Tell me when your team is ready.") }
    }

    /** The clock only starts now: from the host, or [playerId] on the acting team pressing "I'm ready". */
    @Synchronized
    fun charadesStart(playerId: String? = null): Boolean {
        val s = _state.value
        if (s.rulesUp || s.game != Game.Charades || s.turnStarted || s.turnOver) return false
        if (playerId != null && s.player(playerId)?.team != s.actingTeam) return false
        _state.update { it.copy(turnStarted = true, turnEndsAt = now() + if (it.humming) HUM_MS else CHARADES_MS) }
        voice.say("Go!")
        return true
    }

    fun charadesGuessed() {
        val s = _state.value
        if (s.turnOver || !s.turnStarted) return
        s.player(s.actorId)?.let { addPoints(it) }
        _state.update { it.copy(turnOver = true) }
    }

    fun charadesPass() = _state.update { it.copy(turnOver = true) }

    /** Next title, and the other team acts. */
    fun charadesNext() {
        val next = 1 - _state.value.actingTeam
        _state.update { it.copy(titleIndex = it.titleIndex + 1) }
        newCharadesTurn(next)
    }

    // ---------- Guess the Frame

    fun startFrames() = load(Game.Frame) {
        val frames = content.frames()
        _state.update { it.copy(frames = frames, frameIndex = 0, frameLevel = 0, frameOpen = false, frameAnswers = emptyMap(), revealed = false) }
    }

    /** The image has loaded: phones can answer and it starts sharpening. Once fully sharp, time runs out. */
    fun frameShown() {
        _state.update { it.copy(frameOpen = true) }
        frameJob?.cancel()
        frameJob = scope.launch {
            while (true) {
                delay(FRAME_STEP_MS)
                if (_state.value.frameLevel >= FRAME_LAST_LEVEL) {
                    frameReveal()
                    break
                }
                _state.update { it.copy(frameLevel = it.frameLevel + 1) }
            }
        }
    }

    /** One locked-in choice per image, worth what the image was worth at that moment. */
    @Synchronized
    fun frameAnswer(playerId: String, choice: Int): Boolean {
        val s = _state.value
        val frame = s.frame ?: return false
        if (s.game != Game.Frame || !s.frameOpen || s.player(playerId) == null || playerId in s.frameAnswers || choice !in frame.choices.indices) return false
        _state.update { it.copy(frameAnswers = it.frameAnswers + (playerId to FrameAnswer(choice, it.frameWorth))) }
        if (_state.value.frameAnswers.size >= _state.value.players.size) frameReveal()
        return true
    }

    /** Shows the film and pays everyone who picked it. */
    @Synchronized
    fun frameReveal() {
        val s = _state.value
        val frame = s.frame ?: return
        if (s.revealed) return
        frameJob?.cancel()
        s.frameAnswers.forEach { (id, answer) ->
            if (answer.choice == frame.right) s.player(id)?.let { addPoints(it, answer.worth) }
        }
        _state.update { it.copy(revealed = true, frameOpen = false) }
    }

    fun frameNext() {
        frameJob?.cancel()
        _state.value.frame?.let { content.markFramePlayed(it) }
        _state.update { s ->
            s.copy(frameIndex = (s.frameIndex + 1).coerceAtMost(s.frames.size), frameLevel = 0, frameOpen = false, frameAnswers = emptyMap(), revealed = false)
        }
    }

    // ---------- Filmi Bluff

    fun startBluff() = load(Game.Bluff) {
        val bluffs = content.bluffs()
        _state.update { it.copy(bluffs = bluffs, bluffIndex = 0) }
        newBluffRound()
    }

    private fun newBluffRound() {
        _state.update { it.copy(bluffPhase = BluffPhase.Writing, fakes = emptyMap(), bluffOptions = emptyList(), votes = emptyMap(), phaseEndsAt = now() + BLUFF_WRITE_MS) }
    }

    /** Takes a player's lie. Returns what's wrong with it, or null if it was accepted. */
    @Synchronized
    fun bluffWrite(playerId: String, text: String): String? {
        val s = _state.value
        val bluff = s.bluff ?: return "No question right now"
        if (s.rulesUp || s.bluffPhase != BluffPhase.Writing || s.player(playerId) == null) return "Too late for this one"
        if (playerId in s.fakes) return "You've already written one"
        val lie = text.trim().take(40)
        if (lie.isEmpty()) return "Write something first"
        if (same(lie, bluff.answer)) return "That's actually true! Write a lie"
        if (s.fakes.values.any { same(it, lie) }) return "Someone wrote that already. Try another"
        _state.update { it.copy(fakes = it.fakes + (playerId to lie)) }
        if (_state.value.fakes.size >= _state.value.players.size) bluffVoting()
        return null
    }

    /** Shuffles the truth in with the lies. The TV fits twelve answers, and at least four keep it a guess. */
    @Synchronized
    fun bluffVoting() {
        val s = _state.value
        val bluff = s.bluff ?: return
        if (s.bluffPhase != BluffPhase.Writing) return
        val lies = s.fakes.entries.shuffled().take(MAX_LIES).map { BluffOption(it.value, it.key) }
        val ours = bluff.decoys.filter { d -> lies.none { same(it.text, d) } }.shuffled().take((3 - lies.size).coerceAtLeast(0)).map { BluffOption(it, null) }
        val options = (lies + ours + BluffOption(bluff.answer, null, truth = true)).shuffled()
        _state.update { it.copy(bluffPhase = BluffPhase.Voting, bluffOptions = options, phaseEndsAt = now() + BLUFF_VOTE_MS) }
    }

    @Synchronized
    fun bluffVote(playerId: String, option: Int): Boolean {
        val s = _state.value
        val chosen = s.bluffOptions.getOrNull(option) ?: return false
        if (s.bluffPhase != BluffPhase.Voting || s.player(playerId) == null || playerId in s.votes || chosen.author == playerId) return false
        _state.update { it.copy(votes = it.votes + (playerId to option)) }
        if (_state.value.votes.size >= _state.value.players.size) bluffReveal()
        return true
    }

    /** A point for spotting the truth, and a point for each player on the other team your lie fooled. */
    @Synchronized
    fun bluffReveal() {
        val s = _state.value
        if (s.bluffPhase != BluffPhase.Voting) return
        s.votes.forEach { (voterId, option) ->
            val voter = s.player(voterId) ?: return@forEach
            val chosen = s.bluffOptions[option]
            val author = s.player(chosen.author)
            when {
                chosen.truth -> addPoints(voter)
                author != null && author.team != voter.team -> addPoints(author)
            }
        }
        _state.update { it.copy(bluffPhase = BluffPhase.Reveal) }
        s.bluff?.let { voice.say("The truth is ${it.answer}") }
    }

    fun bluffNext() {
        _state.value.bluff?.let { content.markBluffPlayed(it) }
        _state.update { it.copy(bluffIndex = (it.bluffIndex + 1).coerceAtMost(it.bluffs.size)) }
        newBluffRound()
    }

    private fun same(a: String, b: String) = a.normalised() == b.normalised()

    private fun String.normalised() = lowercase().filter { it.isLetterOrDigit() }

    // ---------- Tambola

    fun startTambola() {
        autoCallJob?.cancel()
        _state.update { s ->
            s.copy(
                game = Game.Tambola,
                tickets = s.players.associate { it.id to Tambola.ticket() },
                called = emptyList(),
                claims = emptyMap(),
                banner = null,
                autoCall = false,
            )
        }
        announce("Housie Full! Everyone has a ticket on their phone. Eyes down.")
    }

    @Synchronized
    fun callNumber() {
        val s = _state.value
        val remaining = (1..90) - s.called.toSet()
        if (remaining.isEmpty()) return
        val n = remaining.random()
        _state.update { it.copy(called = it.called + n, banner = null) }
        voice.say(Tambola.call(n))
    }

    fun toggleAutoCall() {
        val on = !_state.value.autoCall
        _state.update { it.copy(autoCall = on) }
        autoCallJob?.cancel()
        if (on) {
            autoCallJob = scope.launch {
                while (true) {
                    callNumber()
                    delay(AUTO_CALL_MS)
                }
            }
        }
    }

    /** Checks a claim against the numbers called so far, not the marks on the phone. */
    @Synchronized
    fun claim(playerId: String, claim: Claim): String {
        val s = _state.value
        val player = s.player(playerId) ?: return "Not in the game"
        val ticket = s.tickets[playerId] ?: return "No ticket"
        s.claims[claim]?.let { return "${claim.label} already won by ${s.player(it)?.name}" }
        val ok = Tambola.check(ticket, s.called.toSet(), claim)
        if (ok) {
            _state.update { it.copy(claims = it.claims + (claim to playerId), banner = "🎉 ${player.name} wins ${claim.label}!") }
            addPoints(player, if (claim == Claim.FullHouse) 3.0 else 1.0)
            voice.say("${claim.label} for ${player.name}!")
            if (claim == Claim.FullHouse) {
                autoCallJob?.cancel()
                _state.update { it.copy(autoCall = false) }
            }
        } else {
            _state.update { it.copy(banner = "Bogey! ${player.name}'s ${claim.label} claim was wrong") }
            voice.say("Bogey!")
        }
        return if (ok) "Yes! ${claim.label}" else "Not yet: that's a bogey"
    }

    // ---------- helpers

    private fun load(game: Game, block: suspend () -> Unit) {
        autoCallJob?.cancel()
        frameJob?.cancel()
        _state.update { it.copy(game = game, loading = true, banner = null, revealed = false, buzzOpen = false, buzzedBy = null, autoCall = false) }
        scope.launch {
            runCatching { block() }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val CHARADES_MS = 90_000L
        const val VERDICT_EVERY = 10
        const val HUM_MS = 60_000L
        const val FRAME_STEP_MS = 4_000L
        const val FRAME_LAST_LEVEL = 7
        const val BLUFF_WRITE_MS = 60_000L
        const val BLUFF_VOTE_MS = 30_000L
        const val MAX_LIES = 11
        const val AUTO_CALL_MS = 9_000L
    }
}

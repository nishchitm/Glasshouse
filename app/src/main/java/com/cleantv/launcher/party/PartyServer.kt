package com.cleantv.launcher.party

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.Inet4Address

/**
 * The page guests open on their phones (via the QR code) and the small API it polls. Home-network only, plain
 * HTTP, no accounts: a player is just a random id their phone keeps.
 */
class PartyServer(private val context: Context, private val party: Party) : NanoHTTPD(PORT) {
    // Every phone asks several times a second, so this is only looked up once.
    private val version by lazy { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }

    /**
     * e.g. "http://192.168.1.20:8080": the TV's address on the home Wi-Fi (or cable), or null if it has none.
     * Not the "active" network: with the VPN on, that's the tunnel (10.x), which phones can't reach.
     */
    @Suppress("DEPRECATION") // allNetworks: the only way to list the non-default (Wi-Fi under a VPN) networks
    fun url(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ip = cm.allNetworks.asSequence()
            .filter { n ->
                val caps = cm.getNetworkCapabilities(n) ?: return@filter false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }
            .flatMap { cm.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
        return ip?.let { "http://$it:$PORT" }
    }

    override fun serve(session: IHTTPSession): Response = try {
        val params = mutableMapOf<String, String>()
        if (session.method == Method.POST) session.parseBody(mutableMapOf())
        session.parameters.forEach { (k, v) -> v.firstOrNull()?.let { params[k] = it } }
        val id = params["id"].orEmpty()
        when (session.uri) {
            "/", "/index.html" -> asset("party/index.html", "text/html; charset=utf-8")
            "/api/join" -> {
                val p = party.join(params["name"].orEmpty(), params["team"]?.toIntOrNull())
                json(buildJsonObject { put("id", p.id) })
            }
            "/api/state" -> json(stateFor(id))
            "/api/buzz" -> json(buildJsonObject { put("ok", party.buzz(id)) })
            "/api/ready" -> json(buildJsonObject { put("ok", party.charadesStart(id)) })
            "/api/frame" -> json(buildJsonObject { put("ok", party.frameAnswer(id, params["choice"]?.toIntOrNull() ?: -1)) })
            "/api/fake" -> json(buildJsonObject { put("message", party.bluffWrite(id, params["text"].orEmpty())) })
            "/api/vote" -> json(buildJsonObject { put("ok", party.bluffVote(id, params["option"]?.toIntOrNull() ?: -1)) })
            "/api/claim" -> {
                val claim = Claim.entries.firstOrNull { it.name == params["claim"] }
                json(buildJsonObject { put("message", claim?.let { party.claim(id, it) } ?: "Unknown claim") })
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    } catch (e: Exception) {
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
    }

    /** What one phone needs to draw its screen. Only the actor's phone ever gets the charades title. */
    private fun stateFor(id: String): JsonObject {
        val s = party.state.value
        val me = s.player(id)
        return buildJsonObject {
            put("joined", me != null)
            me?.let {
                put("name", it.name)
                put("team", it.team)
                put("teamName", TeamNames[it.team])
                put("character", it.character)
                put("rank", s.players.sortedWith(compareByDescending<Player> { p -> s.playerScores[p.id] ?: 0.0 }.thenBy { p -> p.name }).indexOf(it) + 1)
                put("myScore", s.playerScores[it.id] ?: 0.0)
            }
            putJsonArray("teams") { TeamNames.forEach { add(it) } }
            putJsonArray("scores") { s.scores.forEach { add(it) } }
            put("game", s.game.name)
            put("gameLabel", s.label)
            // Changes with each install, so open phone pages reload themselves instead of running an old copy.
            put("version", version)
            put("loading", s.loading)
            put("rulesUp", s.rulesUp)
            if (s.rulesUp && s.game != Game.Lobby) {
                val rules = s.rules
                putJsonObject("rules") {
                    put("title", rules.title)
                    put("tagline", rules.tagline)
                    put("players", rules.players)
                    put("scoring", rules.scoring)
                    putJsonArray("steps") { rules.steps.forEach { add(it) } }
                }
            }
            when (s.game) {
                Game.Emoji -> putJsonObject("buzz") {
                    put("open", s.buzzOpen && s.buzzedBy == null && id !in s.lockedOut)
                    put("winner", s.player(s.buzzedBy)?.name)
                    put("mine", s.buzzedBy == id)
                    put("lockedOut", id in s.lockedOut)
                    put("verdict", s.verdict)
                    put("revealed", s.revealed)
                }
                Game.Charades -> putJsonObject("charades") {
                    val actor = s.player(s.actorId)
                    put("actor", actor?.name)
                    put("actorTeam", TeamNames[s.actingTeam])
                    put("me", s.actorId == id)
                    if (s.actorId == id && !s.turnOver) put("title", s.title)
                    put("turnOver", s.turnOver)
                    put("started", s.turnStarted)
                    put("myTeam", me?.team == s.actingTeam)
                    put("hum", s.humming)
                }
                // The right choice stays on the TV until the reveal.
                Game.Frame -> s.frame?.let { frame ->
                    putJsonObject("frame") {
                        val mine = s.frameAnswers[id]
                        put("index", s.frameIndex)
                        put("open", s.frameOpen)
                        put("revealed", s.revealed)
                        put("mine", mine?.choice ?: -1)
                        put("worth", mine?.worth ?: 0.0)
                        putJsonArray("choices") { frame.choices.forEach { add(it) } }
                        if (s.revealed) put("right", frame.right)
                    }
                }
                // The truth and who wrote what stay on the TV until the reveal.
                Game.Bluff -> s.bluff?.let { bluff ->
                    putJsonObject("bluff") {
                        val reveal = s.bluffPhase == BluffPhase.Reveal
                        put("phase", s.bluffPhase.name)
                        put("index", s.bluffIndex)
                        put("prompt", bluff.prompt)
                        put("mine", s.fakes[id])
                        put("myVote", s.votes[id] ?: -1)
                        putJsonArray("options") {
                            s.bluffOptions.forEachIndexed { i, option ->
                                add(buildJsonObject {
                                    put("i", i)
                                    put("text", option.text)
                                    put("own", option.author == id)
                                    if (reveal) {
                                        put("truth", option.truth)
                                        put("author", s.player(option.author)?.name)
                                    }
                                })
                            }
                        }
                        if (reveal) {
                            put("answer", bluff.answer)
                            put("fooled", s.votes.count { (voter, option) -> s.bluffOptions[option].author == id && s.player(voter)?.team != me?.team })
                        }
                    }
                }
                Game.Tambola -> putJsonObject("tambola") {
                    putJsonArray("ticket") {
                        s.tickets[id]?.forEach { row -> addJsonArray { row.forEach { add(it) } } }
                    }
                    putJsonArray("called") { s.called.forEach { add(it) } }
                    putJsonArray("claims") {
                        Claim.entries.forEach { c ->
                            add(buildJsonObject {
                                put("id", c.name)
                                put("label", c.label)
                                put("winner", s.player(s.claims[c])?.name)
                            })
                        }
                    }
                }
                Game.Lobby -> Unit
            }
        }
    }

    private fun asset(path: String, mime: String): Response =
        newFixedLengthResponse(
            Response.Status.OK, mime,
            context.assets.open(path).bufferedReader().use { it.readText() }
                .replace("{{PARTY}}", PartyName.full.replace("&", "&amp;").replace("<", "&lt;")),
        )
            .apply { addHeader("Cache-Control", "no-store") }

    private fun json(body: JsonObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    companion object {
        const val PORT = 8080
    }
}

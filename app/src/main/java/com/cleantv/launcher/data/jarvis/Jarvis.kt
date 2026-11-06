package com.cleantv.launcher.data.jarvis

import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import com.cleantv.launcher.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/** Something Jarvis was asked to do rather than (or as well as) recommend. */
sealed interface JarvisAction {
    data class OpenApp(val name: String) : JarvisAction
    data class Play(val media: Media) : JarvisAction
    data class Lights(val preset: String) : JarvisAction // on, off, movie, bright, gold, or a Govee scene name
    data class Sleep(val minutes: Int) : JarvisAction
}

/** [topService]: where the first pick streams for the viewer, if on one of their services. */
data class JarvisAnswer(
    val reply: String,
    val titles: List<Media>,
    val topService: StreamingService? = null,
    val action: JarvisAction? = null,
)

/** What the TV can do, so Jarvis only offers real apps and light scenes. */
data class JarvisContext(val region: Region, val liked: List<String>, val apps: List<String>, val scenes: List<String>)

/**
 * The assistant. Gemini turns a plain-language request ("something funny and short like Panchayat", "open
 * YouTube", "movie mode") into specific titles and/or an action; TMDB then supplies the real artwork and data,
 * and whatever streams on the user's services is moved to the front. It remembers the conversation until
 * [reset], so "something shorter" or "only on Netflix" build on the last answer.
 */
class Jarvis(private val gemini: Gemini, private val repo: TmdbRepository) {
    private data class Turn(val user: String, val reply: String, val titles: List<String>)
    private val history = ArrayDeque<Turn>()

    fun reset() = history.clear()

    suspend fun ask(request: String, ctx: JarvisContext, onlyMine: Boolean = false): JarvisAnswer = coroutineScope {
        val response = gemini.generate(body(request, ctx))
        val text = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Jarvis had no answer")
        val picks = json.decodeFromString<Picks>(text)

        // Look every pick up on TMDB at once; names TMDB can't place are dropped.
        val found = picks.titles.map { p ->
            async {
                val type = if (p.type == "movie") MediaType.Movie else MediaType.Tv
                runCatching { repo.findTitle(p.title, p.year, type) }.getOrNull()
            }
        }.awaitAll().filterNotNull().distinctBy { it.key }

        // Streamable here first, keeping Gemini's order within each group.
        val services = found.map { m ->
            async { runCatching { repo.availability(m, ctx.region).services }.getOrDefault(emptyList()) }
        }.awaitAll()
        val ordered = found.zip(services)
            .filter { !onlyMine || it.second.isNotEmpty() || picks.action == "play" }
            .sortedByDescending { it.second.isNotEmpty() }

        val action = when (picks.action) {
            "open_app" -> picks.target?.let { JarvisAction.OpenApp(it) }
            // "Play X": the first title, which the prompt says to put first.
            "play" -> found.firstOrNull()?.let { JarvisAction.Play(it) }
            "lights" -> picks.target?.let { JarvisAction.Lights(it) }
            "sleep" -> picks.minutes?.takeIf { it in 0..240 }?.let { JarvisAction.Sleep(it) }
            else -> null
        }
        history.addLast(Turn(request, picks.reply, ordered.take(6).map { it.first.title }))
        while (history.size > 6) history.removeFirst()
        JarvisAnswer(picks.reply, ordered.map { it.first }, ordered.firstOrNull()?.second?.firstOrNull(), action)
    }

    private fun body(request: String, ctx: JarvisContext) = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", SYSTEM) } }
        }
        putJsonArray("contents") {
            // Earlier turns, so follow-ups ("something shorter") make sense.
            history.forEach { turn ->
                userTurn(turn.user)
                addJsonObject {
                    put("role", "model")
                    putJsonArray("parts") {
                        addJsonObject { put("text", turn.reply + if (turn.titles.isEmpty()) "" else " (Shown: ${turn.titles.joinToString()})") }
                    }
                }
            }
            userTurn(
                """
                Today: ${LocalDate.now()}. Viewer streams in ${ctx.region.label}.
                Their services: ${StreamingService.entries.joinToString { it.label }}.
                Apps installed on the TV: ${ctx.apps.joinToString()}.
                Light scenes: on, off, movie, bright, gold${ctx.scenes.joinToString("") { ", $it" }}.
                Titles they liked recently: ${ctx.liked.ifEmpty { listOf("none yet") }.joinToString("; ")}.
                Request: $request
                """.trimIndent(),
            )
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("temperature", 0.7)
            putJsonObject("responseSchema") {
                put("type", "OBJECT")
                putJsonArray("required") { add("reply"); add("titles"); add("action") }
                putJsonObject("properties") {
                    putJsonObject("reply") { put("type", "STRING") }
                    putJsonObject("action") {
                        put("type", "STRING")
                        putJsonArray("enum") { listOf("none", "open_app", "play", "lights", "sleep").forEach { add(it) } }
                    }
                    putJsonObject("target") { put("type", "STRING") }
                    putJsonObject("minutes") { put("type", "INTEGER") }
                    putJsonObject("titles") {
                        put("type", "ARRAY")
                        putJsonObject("items") {
                            put("type", "OBJECT")
                            putJsonArray("required") { add("title"); add("type") }
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "STRING") }
                                putJsonObject("year") { put("type", "INTEGER") }
                                putJsonObject("type") {
                                    put("type", "STRING")
                                    putJsonArray("enum") { add("movie"); add("tv") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun JsonArrayBuilder.userTurn(text: String) = addJsonObject {
        put("role", "user")
        putJsonArray("parts") { addJsonObject { put("text", text) } }
    }

    @Serializable
    private data class Picks(
        val reply: String = "",
        val titles: List<Pick> = emptyList(),
        val action: String = "none",
        val target: String? = null,
        val minutes: Int? = null,
    )

    @Serializable
    private data class Pick(val title: String, val year: Int? = null, val type: String = "tv")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        val SYSTEM = """
            You are Jarvis, the assistant on a TV. The viewer talks to you in plain words, in English or Hinglish.
            Answer with:
            - reply: one short, friendly spoken-style sentence (max 20 words). It is read aloud, so no lists,
              emoji or symbols.
            - action: what to do on the TV, if they asked for something:
              "open_app" (target = the app's name, exactly as in the installed list),
              "play" (put the title they named first in titles),
              "lights" (target = one of the light scenes listed),
              "sleep" (minutes until the TV turns off; 0 cancels the timer),
              or "none" when they just want suggestions.
            - titles: up to 12 real, specific movies or series that best match a request for something to watch,
              with release year and type; empty for pure TV commands like lights or opening an app.
            Follow-ups build on the conversation so far ("something shorter", "only on Netflix", "more like
            the second one"). Prefer titles on the viewer's services in their country. Use the original release
            title, as listed on TMDB/IMDb. Never invent titles.
        """.trimIndent()
    }
}

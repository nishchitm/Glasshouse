package com.cleantv.launcher.data.f1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.time.Instant
import java.util.concurrent.TimeUnit

data class Session(val name: String, val start: Instant) {
    /** Sources list a session at exactly midnight UTC when only its date is known. */
    val timeKnown get() = start.epochSecond % 86_400 != 0L

    /** Live: started, and within a typical session length. */
    fun isLive(now: Instant = Instant.now()) = now >= start && now < start.plusSeconds(if (name == "Race") 2 * 3600 else 3600)
}

data class Race(val round: Int, val name: String, val circuit: String, val place: String, val sessions: List<Session>) {
    val race get() = sessions.last()
}

data class Driver(val surname: String, val team: String, val headshot: String?, val color: Int)

data class Standing(val position: Int, val driver: Driver, val points: Int)

/** The most interesting championship battle right now. */
data class Fight(val label: String, val ahead: Standing, val behind: Standing, val note: String) {
    val gap get() = ahead.points - behind.points
}

/** A Formula 2 weekend. [Session.timeKnown] is false for rounds whose times aren't published yet. */
data class F2Round(val round: Int, val place: String, val country: String, val sessions: List<Session>)

data class F1Week(
    val next: Race?,
    val later: List<Race>,
    val lastRace: String?,
    val podium: List<Driver>,
    val standings: List<Standing>,
    val pointsLeft: Int,
    val f2: List<F2Round>,
) {
    /** The session that's live, or the next one within [withinMinutes]. */
    fun upcoming(withinMinutes: Long, now: Instant = Instant.now()): Session? =
        next?.sessions?.firstOrNull { it.isLive(now) || (it.start > now && it.start < now.plusSeconds(withinMinutes * 60)) }

    /** The session that's live, or else the next one. */
    fun nextSession(now: Instant = Instant.now()): Session? =
        next?.sessions?.firstOrNull { it.isLive(now) || it.start > now }

    /** The title fight while it's still open; once it's decided, the closest battle behind the champion. */
    val fight: Fight?
        get() {
            val (leader, second) = standings.takeIf { it.size >= 2 } ?: return null
            if (leader.points - second.points <= pointsLeft) {
                return Fight("TITLE FIGHT", leader, second, "$pointsLeft points still to win")
            }
            val (ahead, behind) = standings.drop(1).zipWithNext().minByOrNull { (a, b) -> a.points - b.points } ?: return null
            return Fight("FIGHT FOR P${ahead.position}", ahead, behind, "🏆 ${leader.driver.surname} has the title")
        }
}

/** Formula 1 schedule, results and standings from Jolpica (the free, open successor to the Ergast API). */
class F1 {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cached: Pair<Long, F1Week>? = null
    private var lastFaces: Map<String, Pair<String?, Int>> = emptyMap()

    /** Cached for 30 minutes: results and standings change only after a session. */
    suspend fun week(): F1Week {
        mutex.withLock { cached?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000 }?.let { return it.second } }
        val week = coroutineScope {
            val schedule = async { get("current.json") }
            val last = async { runCatching { get("current/last/results.json") }.getOrNull() }
            val drivers = async { runCatching { get("current/driverStandings.json") }.getOrNull() }
            val photos = async { faces() }
            val f2 = async { runCatching { f2Rounds() }.getOrDefault(emptyList()) }
            val races = schedule.await().table("RaceTable", "Races").map { parseRace(it.jsonObject) }
            val now = Instant.now()
            val faces = photos.await()
            fun driver(d: JsonObject, team: String): Driver {
                val surname = d.str("familyName").orEmpty()
                val face = faces[plain(surname)]
                return Driver(surname, team, face?.first, face?.second ?: teamColor(team))
            }
            val lastRace = last.await()?.table("RaceTable", "Races")?.firstOrNull()?.jsonObject
            val list = drivers.await()?.table("StandingsTable", "StandingsLists")?.firstOrNull()?.jsonObject
            val standingsRound = list?.str("round")?.toIntOrNull() ?: 0
            F1Week(
                next = races.firstOrNull { it.race.start.plusSeconds(3 * 3600) > now },
                later = races.filter { it.race.start.plusSeconds(3 * 3600) > now }.drop(1).take(3),
                lastRace = lastRace?.str("raceName"),
                podium = lastRace?.get("Results")?.jsonArray.orEmpty().take(3).map { r ->
                    val o = r.jsonObject
                    driver(o["Driver"]!!.jsonObject, o["Constructor"]?.jsonObject?.str("name").orEmpty())
                },
                standings = list?.get("DriverStandings")?.jsonArray.orEmpty().take(6).map { s ->
                    val o = s.jsonObject
                    Standing(
                        o.str("position")!!.toInt(),
                        driver(o["Driver"]!!.jsonObject, o["Constructors"]?.jsonArray?.firstOrNull()?.jsonObject?.str("name").orEmpty()),
                        o.str("points")?.toDoubleOrNull()?.toInt() ?: 0,
                    )
                },
                // A win is 25, a sprint win 8.
                pointsLeft = races.filter { it.round > standingsRound }
                    .sumOf { r -> 25 + if (r.sessions.any { it.name == "Sprint" }) 8 else 0 },
                f2 = f2.await(),
            )
        }
        return week.also { mutex.withLock { cached = System.currentTimeMillis() to it } }
    }

    /** Driver photos and team colours from OpenF1, by surname; the last good answer is kept, as it can refuse during live sessions. */
    private suspend fun faces(): Map<String, Pair<String?, Int>> {
        val fresh = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url("https://api.openf1.org/v1/drivers?session_key=latest").build()
                http.newCall(request).execute().use { json.parseToJsonElement(it.body!!.string()).jsonArray }
            }.associate { e ->
                val o = e.jsonObject
                val color = o["team_colour"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(16) ?: 0x888888
                plain(o.str("last_name").orEmpty()) to (o["headshot_url"]?.jsonPrimitive?.contentOrNull?.replace("/1col/", "/2col/") to color)
            }
        }.getOrNull()
        return mutex.withLock {
            fresh?.let { lastFaces = it }
            lastFaces
        }
    }

    private fun plain(name: String) = Normalizer.normalize(name, Normalizer.Form.NFD).filter { it.isLetter() && it.code < 128 }.lowercase()

    private fun teamColor(team: String) = TEAM_COLORS.entries.firstOrNull { team.contains(it.key, ignoreCase = true) }?.value ?: 0x888888

    /** The next two Formula 2 weekends, from TheSportsDB (Jolpica only covers F1). */
    private suspend fun f2Rounds(): List<F2Round> {
        val next = sportsDb("eventsnextleague.php?id=$F2_LEAGUE").firstOrNull() ?: return emptyList()
        val round = next.str("intRound")?.toIntOrNull() ?: return emptyList()
        val season = next.str("strSeason").orEmpty()
        return listOf(round, round + 1).mapNotNull { r ->
            val events = runCatching { sportsDb("eventsround.php?id=$F2_LEAGUE&r=$r&s=$season") }.getOrDefault(emptyList())
            val sessions = events.mapNotNull { e ->
                val name = e.str("strEvent") ?: return@mapNotNull null
                val label = F2_SESSIONS.firstOrNull { name.endsWith(it) } ?: return@mapNotNull null
                val at = runCatching { Instant.parse("${e.str("strTimestamp")}Z") }.getOrNull() ?: return@mapNotNull null
                name.removeSuffix(label).trim() to Session(label, at)
            }
            val country = events.firstOrNull()?.str("strCountry").orEmpty()
            sessions.firstOrNull()?.let { (place, _) -> F2Round(r, place, country, sessions.map { it.second }.sortedBy { it.start }) }
        }
    }

    private suspend fun sportsDb(path: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://www.thesportsdb.com/api/v1/json/123/$path").build()
        http.newCall(request).execute().use { r ->
            json.parseToJsonElement(r.body!!.string()).jsonObject["events"]?.takeIf { it !is JsonNull }?.jsonArray.orEmpty().map { it.jsonObject }
        }
    }

    private fun parseRace(o: JsonObject): Race {
        fun at(s: JsonObject?) = s?.let { Instant.parse("${it.str("date")}T${it.str("time") ?: "00:00:00Z"}") }
        val named = listOf(
            "FirstPractice" to "Practice 1", "SecondPractice" to "Practice 2", "ThirdPractice" to "Practice 3",
            "SprintQualifying" to "Sprint Qualifying", "SprintShootout" to "Sprint Shootout", "Sprint" to "Sprint",
            "Qualifying" to "Qualifying",
        )
        val sessions = named.mapNotNull { (key, label) -> at(o[key]?.jsonObject)?.let { Session(label, it) } } +
            Session("Race", at(o)!!)
        val circuit = o["Circuit"]!!.jsonObject
        val location = circuit["Location"]?.jsonObject
        return Race(
            round = o.str("round")!!.toInt(),
            name = o.str("raceName").orEmpty(),
            circuit = circuit.str("circuitName").orEmpty(),
            place = listOfNotNull(location?.str("locality"), location?.str("country")).joinToString(", "),
            sessions = sessions.sortedBy { it.start },
        )
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content
    private fun JsonObject.table(table: String, list: String) =
        this["MRData"]!!.jsonObject[table]!!.jsonObject[list]?.jsonArray.orEmpty()

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.jolpi.ca/ergast/f1/$path").build()
        http.newCall(request).execute().use { json.parseToJsonElement(it.body!!.string()).jsonObject }
    }

    private companion object {
        const val F2_LEAGUE = 4486
        val F2_SESSIONS = listOf("Practice", "Qualifying", "Sprint Race", "Feature Race")
        val TEAM_COLORS = mapOf(
            "McLaren" to 0xF47600, "Mercedes" to 0x00D7B6, "Red Bull" to 0x4781D7, "Ferrari" to 0xED1131,
            "Williams" to 0x1868DB, "Aston" to 0x229971, "Alpine" to 0x00A1E8, "Haas" to 0x9C9FA2,
            "Sauber" to 0x01C00E, "Audi" to 0xF50537, "RB" to 0x6C98FF, "Cadillac" to 0x909090,
        )
    }
}

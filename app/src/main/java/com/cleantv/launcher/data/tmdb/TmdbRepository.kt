package com.cleantv.launcher.data.tmdb

import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.StreamingService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

enum class MediaType(val path: String) { Movie("movie"), Tv("tv") }

data class Media(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    val year: String?,
    val rating: Double,
    val backdropUrl: String,
    val genreIds: List<Int> = emptyList(),
    val language: String? = null,
    val releaseDate: LocalDate? = null,
) {
    /** Not out yet (the Coming soon tab). */
    val upcoming get() = releaseDate?.isAfter(LocalDate.now()) == true

    /** Identity across lists: TMDB ids are only unique per media type. */
    val key get() = "${type.path}-$id"
}

enum class RailKind { Normal, ForYou, Latest, Upcoming, Continue }

/** One tab on the home screen; [tab] is its short label. */
data class Rail(
    val title: String,
    val items: List<Media>,
    val kind: RailKind = RailKind.Normal,
    val tab: String = title,
    /** A line shown above the cards, e.g. Jarvis's reply. */
    val note: String? = null,
)

/** Where a title streams in a region: the user's services first, plus how many other services carry it. */
data class Availability(val services: List<StreamingService>, val otherCount: Int)

class TmdbRepository(private val api: TmdbApi) {
    private val serviceIdsByRegion = mutableMapOf<Region, Map<StreamingService, Set<Int>>>()
    private val availabilityCache = mutableMapOf<Pair<Region, String>, Availability>()
    private val genreNames = mutableMapOf<MediaType, Map<Int, String>>()
    private val imdbIds = mutableMapOf<String, String?>()
    private val mutex = Mutex()

    suspend fun homeRails(region: Region, onlyMine: Boolean = false): List<Rail> = coroutineScope {
        val ids = serviceIds(region).values.flatten().joinToString("|")
        val onMyServices = mapOf(
            "watch_region" to region.code,
            "with_watch_providers" to ids,
            "with_watch_monetization_types" to "flatrate",
            "sort_by" to "popularity.desc",
        )
        // Without any matched provider IDs, discover would ignore the filter and return everything.
        val hasServices = ids.isNotEmpty()
        listOfNotNull(
            if (hasServices) async { latest(onMyServices) } else null,
            async { comingSoon() },
            async {
                val trending = api.trending().toMedia(null)
                Rail("Trending this week", if (onlyMine) keepOnMyServices(trending, region) else trending, tab = "Trending")
            },
            if (hasServices) {
                async { Rail("Series on your services", api.discover("tv", onMyServices).toMedia(MediaType.Tv), tab = "Series") }
            } else null,
            if (hasServices) {
                async { Rail("Movies on your services", api.discover("movie", onMyServices).toMedia(MediaType.Movie), tab = "Movies") }
            } else null,
            if (hasServices) {
                async {
                    val params = onMyServices + ("with_origin_country" to "IN")
                    Rail("Indian series", api.discover("tv", params).toMedia(MediaType.Tv), tab = "Indian")
                }
            } else null,
            async {
                val anime = mapOf("with_genres" to "16", "with_origin_country" to "JP", "sort_by" to "popularity.desc")
                val params = if (onlyMine && hasServices) anime + onMyServices.filterKeys { it != "sort_by" } else anime
                Rail("Anime", api.discover("tv", params).toMedia(MediaType.Tv), tab = "Anime")
            },
        ).awaitAll().filter { it.items.isNotEmpty() }
    }

    /**
     * What's new across the user's subscriptions: series with an episode in the last 10 days and movies
     * released in the last ~4 months, each by popularity, interleaved.
     */
    private suspend fun latest(onMyServices: Map<String, String>): Rail = coroutineScope {
        val today = LocalDate.now()
        val shows = async {
            val params = onMyServices + mapOf(
                "air_date.gte" to today.minusDays(10).toString(),
                "air_date.lte" to today.toString(),
            )
            api.discover("tv", params).toMedia(MediaType.Tv)
        }
        val movies = async {
            val params = onMyServices + mapOf(
                "primary_release_date.gte" to today.minusDays(120).toString(),
                "primary_release_date.lte" to today.toString(),
            )
            api.discover("movie", params).toMedia(MediaType.Movie)
        }
        val s = shows.await()
        val m = movies.await()
        val mixed = (0 until maxOf(s.size, m.size)).flatMap { listOfNotNull(s.getOrNull(it), m.getOrNull(it)) }
        Rail("Latest on your services", mixed, RailKind.Latest, tab = "Latest")
    }

    /**
     * The big films of the coming year, in cinemas or straight to streaming: unreleased movies by popularity,
     * which TMDB drives from searches and buzz, so the famous ones (Dune, Avengers…) float to the top.
     */
    private suspend fun comingSoon(): Rail {
        val today = LocalDate.now()
        val params = mapOf(
            "primary_release_date.gte" to today.plusDays(1).toString(),
            "primary_release_date.lte" to today.plusYears(1).toString(),
            "sort_by" to "popularity.desc",
        )
        val pages = coroutineScope { (1..2).map { p -> async { api.discover("movie", params + ("page" to "$p")) } }.awaitAll() }
        // The most-anticipated 25, then soonest first.
        val movies = pages.flatMap { it.toMedia(MediaType.Movie) }.distinctBy { it.key }.take(25).sortedBy { it.releaseDate }
        return Rail("Coming soon", movies, RailKind.Upcoming, tab = "Coming soon")
    }

    suspend fun search(query: String): List<Media> = api.search(query).toMedia(null)

    /**
     * The titles that stream on the user's services in [region], keeping their order. Lookups run in batches
     * and stop once [limit] are found, since each one is a network call.
     */
    suspend fun keepOnMyServices(items: List<Media>, region: Region, limit: Int = Int.MAX_VALUE): List<Media> {
        val kept = mutableListOf<Media>()
        for (batch in items.chunked(10)) {
            val ok = coroutineScope {
                batch.map { m -> async { runCatching { availability(m, region).services.isNotEmpty() }.getOrDefault(false) } }.awaitAll()
            }
            kept += batch.zip(ok).filter { it.second }.map { it.first }
            if (kept.size >= limit) break
        }
        return kept.take(limit)
    }

    /** The best TMDB match for an exact title, e.g. one Jarvis named. The year narrows remakes and namesakes. */
    suspend fun findTitle(title: String, year: Int?, type: MediaType): Media? {
        val page = if (type == MediaType.Movie) {
            api.searchType("movie", title, year = year)
        } else {
            api.searchType("tv", title, firstAirYear = year)
        }
        // A wrong year (common for long-running shows) finds nothing; retry without it.
        val results = page.toMedia(type).ifEmpty {
            if (year == null) emptyList() else api.searchType(type.path, title).toMedia(type)
        }
        return results.firstOrNull()
    }

    /** A wide image of a Hindi film with no title printed on it, for Guess the Frame. */
    suspend fun filmStill(title: String, year: Int?): String? {
        val results = api.searchType("movie", title, year = year).results
        val film = results.firstOrNull { it.originalLanguage == "hi" } ?: results.firstOrNull() ?: return null
        return api.movieImages(film.id).backdrops.filter { it.language == null }
            .sortedByDescending { it.votes }.take(5).randomOrNull()
            ?.let { "https://image.tmdb.org/t/p/w1280${it.path}" }
    }

    suspend fun recommendations(media: Media): List<Media> =
        api.recommendations(media.type.path, media.id).toMedia(null)

    /**
     * Popular titles streaming on the user's services in [region], optionally narrowed to any of [genres]
     * (ids that don't exist for a media type are dropped, since TMDB's movie and TV genre ids differ).
     */
    suspend fun discoverOnServices(region: Region, genres: Collection<Int> = emptyList(), page: Int = 1): List<Media> {
        val ids = serviceIds(region).values.flatten().joinToString("|").ifEmpty { return emptyList() }
        return coroutineScope {
            MediaType.entries.map { type ->
                async {
                    val valid = genres.filter { it in genreNames(type) }
                    if (genres.isNotEmpty() && valid.isEmpty()) return@async emptyList()
                    val params = buildMap {
                        put("watch_region", region.code)
                        put("with_watch_providers", ids)
                        put("with_watch_monetization_types", "flatrate")
                        put("sort_by", "popularity.desc")
                        put("page", page.toString())
                        if (valid.isNotEmpty()) put("with_genres", valid.joinToString("|"))
                    }
                    api.discover(type.path, params).toMedia(type)
                }
            }.awaitAll().flatten()
        }
    }

    /** e.g. "tt12004706", or null when TMDB doesn't know the title's IMDb page. */
    suspend fun imdbId(media: Media): String? {
        mutex.withLock { if (media.key in imdbIds) return imdbIds[media.key] }
        val id = api.externalIds(media.type.path, media.id).imdbId?.takeIf { it.startsWith("tt") }
        return id.also { mutex.withLock { imdbIds[media.key] = it } }
    }

    suspend fun genreNames(type: MediaType): Map<Int, String> {
        mutex.withLock { genreNames[type] }?.let { return it }
        val names = api.genres(type.path).genres.associate { it.id to it.name }
        return names.also { mutex.withLock { genreNames[type] = it } }
    }

    suspend fun availability(media: Media, region: Region): Availability {
        mutex.withLock { availabilityCache[region to media.key] }?.let { return it }
        val r = api.watchProviders(media.type.path, media.id).results[region.code]
        val providers = r?.let { it.flatrate + it.free + it.ads }.orEmpty().distinctBy { it.id }
        val mine = StreamingService.entries.filter { s -> providers.any { s.matchesProvider(it.name) } }
        val others = providers.count { p -> mine.none { it.matchesProvider(p.name) } }
        return Availability(mine, others).also { mutex.withLock { availabilityCache[region to media.key] = it } }
    }

    // TMDB provider IDs vary by region (Prime is 119 in India, 9 elsewhere), so resolve them by name.
    private suspend fun serviceIds(region: Region): Map<StreamingService, Set<Int>> {
        mutex.withLock { serviceIdsByRegion[region] }?.let { return it }
        val catalog = coroutineScope {
            listOf(async { api.providerCatalog("movie", region.code) }, async { api.providerCatalog("tv", region.code) })
                .awaitAll().flatMap { it.results }
        }
        val ids = StreamingService.entries.associateWith { s ->
            catalog.filter { s.matchesProvider(it.name) }.map { it.id }.toSet()
        }.filterValues { it.isNotEmpty() }
        return ids.also { mutex.withLock { serviceIdsByRegion[region] = it } }
    }
}

private fun Page.toMedia(forcedType: MediaType?): List<Media> = results.mapNotNull { m ->
    val type = forcedType ?: when (m.mediaType) {
        "movie" -> MediaType.Movie
        "tv" -> MediaType.Tv
        else -> return@mapNotNull null // people
    }
    val backdrop = m.backdropPath ?: return@mapNotNull null
    Media(
        id = m.id,
        type = type,
        title = m.title ?: m.name ?: return@mapNotNull null,
        overview = m.overview,
        year = (m.releaseDate ?: m.firstAirDate)?.take(4)?.takeIf { it.isNotBlank() },
        rating = m.voteAverage,
        backdropUrl = "https://image.tmdb.org/t/p/w1280$backdrop",
        genreIds = m.genreIds,
        language = m.originalLanguage,
        releaseDate = (m.releaseDate ?: m.firstAirDate)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )
}

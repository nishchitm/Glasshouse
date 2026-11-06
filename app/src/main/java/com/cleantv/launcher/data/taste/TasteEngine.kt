package com.cleantv.launcher.data.taste

import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The on-device recommender. Everything it learns lives in [TasteDb]; TMDB only supplies candidates.
 *
 * Candidates come from three places: TMDB "recommendations" for recently liked titles, popular titles on the
 * user's services in their favourite genres, and plain popular titles on their services (so a fresh install
 * still has a deck). Each is scored against a profile of genre and language weights built from every rating.
 */
class TasteEngine(private val repo: TmdbRepository, private val dao: TasteDao) {

    suspend fun rate(media: Media, signal: Signal) = dao.upsert(RatedTitle.of(media, signal))

    suspend fun watchlist(): List<Media> = dao.bySignal(Signal.Watchlist).map { it.toMedia() }

    suspend fun ratedCount(): Int = dao.all().size

    /** Recently liked or saved titles, as names: context for Jarvis. */
    suspend fun likedTitles(limit: Int = 12): List<String> = dao.all()
        .filter { it.signal == Signal.Like || it.signal == Signal.Watchlist }
        .take(limit)
        .map { listOfNotNull(it.title, it.year?.let { y -> "($y)" }).joinToString(" ") }

    /** Unrated titles, best match first. [exclude] holds [Media.key]s already queued elsewhere. */
    suspend fun recommend(region: Region, limit: Int, exclude: Set<String> = emptySet(), onlyMine: Boolean = false): List<Media> = coroutineScope {
        val rated = dao.all()
        val ratedKeys = rated.map { "${it.type}-${it.id}" }.toSet()
        val profile = Profile(rated)
        val seeds = rated.filter { it.signal == Signal.Like || it.signal == Signal.Watchlist }.take(8)

        val fromSeeds = seeds.map { seed ->
            async { runCatching { repo.recommendations(seed.toMedia()) }.getOrDefault(emptyList()) }
        }
        val inFavouriteGenres = async {
            val top = profile.topGenres(3)
            if (top.isEmpty()) emptyList() else runCatching { repo.discoverOnServices(region, top) }.getOrDefault(emptyList())
        }
        // Deeper pages once there's history, so the deck doesn't keep serving the same top-20.
        val popular = async {
            val page = 1 + (rated.size / 40).coerceAtMost(4)
            runCatching { repo.discoverOnServices(region, page = page) }.getOrDefault(emptyList())
        }

        val recommended = fromSeeds.awaitAll().flatten()
        val recommendedBy = recommended.groupingBy { it.key }.eachCount()
        val pool = (recommended + inFavouriteGenres.await() + popular.await())
            .distinctBy { it.key }
            .filter { it.key !in ratedKeys && it.key !in exclude }

        val random = Random(System.currentTimeMillis())
        val ranked = pool.sortedByDescending { m ->
            1.5f * (recommendedBy[m.key] ?: 0) +
                profile.genreScore(m.genreIds) +
                0.8f * profile.languageScore(m.language) +
                0.1f * m.rating.toFloat() +
                random.nextFloat() * 0.3f // a little shuffle so the deck isn't identical each visit
        }
        if (onlyMine) repo.keepOnMyServices(ranked, region, limit) else ranked.take(limit)
    }

    private class Profile(rated: List<RatedTitle>) {
        private val genre = weights(rated.flatMap { r -> r.genres.map { it to r.signal.weight } })
        private val language = weights(rated.mapNotNull { r -> r.language?.let { it to r.signal.weight } })

        fun topGenres(n: Int) = genre.filterValues { it > 0 }.entries.sortedByDescending { it.value }.take(n).map { it.key }

        // Mean rather than sum, so titles tagged with many genres aren't favoured just for that.
        fun genreScore(ids: List<Int>) = if (ids.isEmpty()) 0f else ids.map { genre[it] ?: 0f }.average().toFloat()

        fun languageScore(lang: String?) = language[lang] ?: 0f

        // Sum of signals damped by sqrt(count): consistent reactions grow a weight, but one genre can't run away.
        private fun <K> weights(signals: List<Pair<K, Float>>) =
            signals.groupBy({ it.first }, { it.second }).mapValues { (_, w) -> w.sum() / sqrt(w.size + 1f) }
    }
}

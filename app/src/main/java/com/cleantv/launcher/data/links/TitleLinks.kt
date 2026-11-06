package com.cleantv.launcher.data.links

import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Each service's own page for a title, looked up on Wikidata (open data, no key) from its TMDB id.
 * TMDB knows *which* services carry a title but not their ids; Wikidata maps between them.
 */
class TitleLinks(private val api: WikidataApi) {
    private val cache = mutableMapOf<String, Map<StreamingService, String>>()
    private val mutex = Mutex()

    suspend fun forTitle(media: Media): Map<StreamingService, String> {
        mutex.withLock { cache[media.key] }?.let { return it }
        // Movie and TV ids overlap on TMDB (155 is a film and a sitcom), so match on the right property.
        val tmdbProperty = if (media.type == MediaType.Movie) "P4947" else "P4983"
        val props = Properties.keys.joinToString(" ") { "wdt:$it" }
        val sparql = """SELECT ?p ?v WHERE { ?item wdt:$tmdbProperty "${media.id}" . VALUES ?p { $props } ?item ?p ?v . }"""
        val rows = api.query(sparql)["results"]?.jsonObject?.get("bindings")?.jsonArray.orEmpty()
        val order = Properties.keys.toList()
        val links = rows.mapNotNull { row ->
            val o = row.jsonObject
            val property = o["p"]?.jsonObject?.get("value")?.jsonPrimitive?.content?.substringAfterLast('/')
            val value = o["v"]?.jsonObject?.get("value")?.jsonPrimitive?.content ?: return@mapNotNull null
            val (service, url) = Properties[property] ?: return@mapNotNull null
            Triple(order.indexOf(property), service, url(value))
        }
            // Where a service has two ids, Properties lists the preferred one last, so it wins in toMap().
            .sortedBy { it.first }
            .associate { it.second to it.third }
        return links.also { mutex.withLock { cache[media.key] = it } }
    }

    private companion object {
        // Wikidata property → (service, how that service's web link is built). Formats are each property's own
        // "formatter URL" on Wikidata; the TV apps register these web links.
        val Properties: Map<String, Pair<StreamingService, (String) -> String>> = linkedMapOf(
            "P1874" to (StreamingService.Netflix to { id -> "https://www.netflix.com/title/$id" }),
            "P14440" to (StreamingService.Prime to { id -> "https://www.primevideo.com/detail/$id" }),
            "P14462" to (StreamingService.Prime to { id -> "https://www.primevideo.com/detail/$id" }), // GTI
            "P9586" to (StreamingService.AppleTv to { id -> "https://tv.apple.com/movie/$id" }),
            "P9751" to (StreamingService.AppleTv to { id -> "https://tv.apple.com/show/$id" }),
            "P11049" to (StreamingService.JioHotstar to { id -> "https://www.hotstar.com/$id" }),
            "P11330" to (StreamingService.Crunchyroll to { id -> "https://www.crunchyroll.com/series/$id" }),
            "P9465" to (StreamingService.SonyLiv to { id -> "https://www.sonyliv.com/shows/$id" }),
        )
    }
}

interface WikidataApi {
    @GET("sparql")
    suspend fun query(@Query("query") sparql: String, @Query("format") format: String = "json"): JsonObject

    companion object {
        fun create(): WikidataApi {
            // Wikidata asks clients to identify themselves.
            val http = OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Glasshouse/0.1 (personal Android TV launcher)")
                            .header("Accept", "application/sparql-results+json")
                            .build(),
                    )
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://query.wikidata.org/")
                .client(http)
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(WikidataApi::class.java)
        }
    }
}

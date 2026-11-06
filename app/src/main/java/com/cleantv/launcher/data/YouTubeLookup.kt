package com.cleantv.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * A YouTube video's link, from its title and channel. The TV app tells Android what's playing but not the video's
 * id, so this reads YouTube's own results page. Only an exact match counts: a search is better than the wrong video.
 */
class YouTubeLookup {
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun link(title: String, channel: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.youtube.com/results".toHttpUrl().newBuilder().addQueryParameter("search_query", "$title $channel").build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept-Language", "en")
                .header("Cookie", "SOCS=CAI") // skips the EU consent page
                .build()
            val page = http.newCall(request).execute().use { it.body?.string() }.orEmpty()
            videos(page).firstOrNull { it.title.same(title) && (channel.isBlank() || it.channel.same(channel)) }
                ?.let { "https://www.youtube.com/watch?v=${it.id}" }
        }.getOrNull()
    }

    private class Video(val id: String, val title: String, val channel: String)

    private fun videos(page: String): List<Video> {
        val found = mutableListOf<Video>()
        var at = page.indexOf(VIDEO)
        while (at >= 0 && found.size < MAX_RESULTS) {
            val start = at + VIDEO.length
            val next = page.indexOf(VIDEO, start).let { if (it < 0) page.length else it }
            val block = page.substring(start, next)
            found += Video(block.substringBefore('"'), block.textAfter(TITLE), block.textAfter(OWNER))
            at = page.indexOf(VIDEO, start)
        }
        return found
    }

    /** The JSON string that follows [marker], unescaped. */
    private fun String.textAfter(marker: String): String {
        val start = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return ""
        var end = start
        while (end < length && this[end] != '"') end += if (this[end] == '\\') 2 else 1
        return runCatching { Json.decodeFromString<String>("\"${substring(start, end)}\"") }.getOrDefault("")
    }

    private fun String.same(other: String) = StreamingService.normalise(this) == StreamingService.normalise(other)

    private companion object {
        const val VIDEO = "\"videoRenderer\":{\"videoId\":\""
        const val TITLE = "\"title\":{\"runs\":[{\"text\":\""
        const val OWNER = "\"ownerText\":{\"runs\":[{\"text\":\""
        const val MAX_RESULTS = 10
    }
}

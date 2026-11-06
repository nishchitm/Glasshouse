package com.cleantv.launcher.data.wallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** A photo to show as the wallpaper, with the credit its licence asks for. */
data class Photo(val url: String, val credit: String)

/**
 * Photo wallpapers: Bing's photo of the day (published for use as wallpaper) and a rotating set of Wikimedia
 * Commons featured landscape photos (Creative Commons; credited on screen).
 */
class PhotoWallpapers {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var daily: Pair<LocalDate, Photo>? = null
    private var curated: List<Photo> = emptyList()

    /** Today's Bing photo (cached for the day). */
    suspend fun daily(): Photo {
        val today = LocalDate.now()
        mutex.withLock { daily?.takeIf { it.first == today }?.let { return it.second } }
        val body = get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-IN")
        val image = body["images"]!!.jsonArray.first().jsonObject
        val photo = Photo(
            url = "https://www.bing.com" + image["url"]!!.jsonPrimitive.content,
            credit = image["copyright"]?.jsonPrimitive?.content.orEmpty(),
        )
        return photo.also { mutex.withLock { daily = today to it } }
    }

    /** One of the featured landscapes; [slot] picks which, e.g. the hour, so the photo changes through the day. */
    suspend fun curated(slot: Int): Photo {
        val list = mutex.withLock { curated }.ifEmpty { loadCurated().also { mutex.withLock { curated = it } } }
        return list[Math.floorMod(slot, list.size)]
    }

    private suspend fun loadCurated(): List<Photo> {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("generator", "categorymembers")
            .addQueryParameter("gcmtitle", "Category:Featured pictures of landscapes")
            .addQueryParameter("gcmtype", "file")
            .addQueryParameter("gcmlimit", "120")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|extmetadata")
            .addQueryParameter("iiurlwidth", "1920")
            .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName")
            .build().toString()
        val pages = get(url)["query"]?.jsonObject?.get("pages")?.jsonObject?.values.orEmpty()
        return pages.mapNotNull { page ->
            val info = page.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val w = info["width"]?.jsonPrimitive?.int ?: 0
            val h = info["height"]?.jsonPrimitive?.int ?: 1
            // Roughly 16:9-ish only: tall photos or very wide panoramas crop badly on a TV.
            if (w.toFloat() / h !in 1.3f..2.1f) return@mapNotNull null
            val meta = info["extmetadata"]?.jsonObject
            fun field(name: String) = meta?.get(name)?.jsonObject?.get("value")?.jsonPrimitive?.content
                ?.replace(Regex("<[^>]+>"), "")?.trim()
            val credit = listOfNotNull(field("Artist"), field("LicenseShortName"), "Wikimedia Commons").joinToString(" · ")
            Photo(info["thumburl"]?.jsonPrimitive?.content ?: return@mapNotNull null, credit)
        }.shuffled(java.util.Random(LocalDate.now().toEpochDay())) // a different order each day
    }

    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "Glasshouse/0.1 (personal Android TV launcher)").build()
        http.newCall(request).execute().use { json.parseToJsonElement(it.body!!.string()).jsonObject }
    }
}

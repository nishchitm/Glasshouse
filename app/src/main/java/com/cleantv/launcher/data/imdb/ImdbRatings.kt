package com.cleantv.launcher.data.imdb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * IMDb ratings from IMDb's own daily dataset (https://developer.imdb.com/non-commercial-datasets/,
 * free for personal use; there is no free IMDb API). The ~9 MB file is fetched at most weekly and
 * boiled down to a ~2 MB sorted table on the TV, so lookups are local and instant.
 */
class ImdbRatings(context: Context) {
    private val file = File(context.filesDir, "imdb_ratings.bin")
    private val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.MINUTES).build()

    // Sorted IMDb ids (the number in "tt0944947") and their ratings ×10. Null until loaded.
    @Volatile private var ids: IntArray? = null
    @Volatile private var ratings: ByteArray? = null

    /** e.g. rating("tt0944947") → 9.2, or null if unknown or not loaded yet. */
    fun rating(imdbId: String): Double? {
        val ids = ids ?: return null
        val n = imdbId.removePrefix("tt").toIntOrNull() ?: return null
        val i = ids.binarySearch(n)
        return if (i >= 0) ratings!![i] / 10.0 else null
    }

    /** Loads the local table, downloading a fresh one first if it's missing or a week old. Throws on network failure. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (ids == null && file.exists()) load()
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (stale) {
            download()
            load()
        }
    }

    private fun download() {
        val request = Request.Builder().url(URL).build()
        val rows = ArrayList<Long>(500_000) // id << 8 | rating×10, so sorting by id keeps pairs together
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "IMDb dataset: HTTP ${response.code}" }
            GZIPInputStream(response.body!!.byteStream()).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line -> // tconst \t averageRating \t numVotes
                    val a = line.indexOf('\t')
                    val b = line.indexOf('\t', a + 1)
                    val votes = line.substring(b + 1).toIntOrNull() ?: 0
                    // Below ~100 votes ratings are noise; skipping them also keeps the table small.
                    if (votes < MIN_VOTES) return@forEach
                    val id = line.substring(2, a).toIntOrNull() ?: return@forEach
                    val rating = (line.substring(a + 1, b).toDoubleOrNull() ?: return@forEach) * 10
                    rows += (id.toLong() shl 8) or Math.round(rating).toLong()
                }
            }
        }
        rows.sort()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(rows.size)
            rows.forEach { out.writeInt((it shr 8).toInt()) }
            rows.forEach { out.writeByte((it and 0xFF).toInt()) }
        }
        tmp.renameTo(file)
    }

    private fun load() {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val n = input.readInt()
            val ids = IntArray(n) { input.readInt() }
            val ratings = ByteArray(n) { input.readByte() }
            this.ratings = ratings
            this.ids = ids
        }
    }

    private companion object {
        const val URL = "https://datasets.imdbws.com/title.ratings.tsv.gz"
        const val MIN_VOTES = 100
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}

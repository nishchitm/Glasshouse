package com.cleantv.launcher.data.taste

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType

/** How the user reacted to a title in Discover. [weight] is how much it pulls the taste profile. */
enum class Signal(val weight: Float) {
    Like(1f),
    Watchlist(1.5f),
    Nope(-1f),

    // "Already seen": they chose to watch it, so a mild positive; mainly keeps it out of the deck.
    Seen(0.5f),
}

/** One rated title, with enough of the TMDB data to show it again (e.g. on the watchlist rail) offline. */
@Entity(tableName = "rated", primaryKeys = ["type", "id"])
data class RatedTitle(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val year: String?,
    val rating: Double,
    val backdropUrl: String,
    val genreIds: String, // comma-separated TMDB genre ids
    val language: String?,
    val signal: Signal,
    val ratedAt: Long,
) {
    val genres get() = genreIds.split(',').mapNotNull { it.toIntOrNull() }

    fun toMedia() = Media(
        id, MediaType.entries.first { it.path == type }, title, overview, year, rating, backdropUrl, genres, language,
    )

    companion object {
        fun of(media: Media, signal: Signal) = RatedTitle(
            media.id, media.type.path, media.title, media.overview, media.year, media.rating, media.backdropUrl,
            media.genreIds.joinToString(","), media.language, signal, System.currentTimeMillis(),
        )
    }
}

@Dao
interface TasteDao {
    @Query("SELECT * FROM rated ORDER BY ratedAt DESC")
    suspend fun all(): List<RatedTitle>

    @Query("SELECT * FROM rated WHERE signal = :signal ORDER BY ratedAt DESC")
    suspend fun bySignal(signal: Signal): List<RatedTitle>

    @Upsert
    suspend fun upsert(title: RatedTitle)
}

@Database(entities = [RatedTitle::class], version = 1, exportSchema = false)
abstract class TasteDb : RoomDatabase() {
    abstract fun dao(): TasteDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, TasteDb::class.java, "taste.db").build()
    }
}

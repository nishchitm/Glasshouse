package com.cleantv.launcher.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

interface TmdbApi {
    @GET("trending/all/week")
    suspend fun trending(): Page

    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @QueryMap params: Map<String, String>): Page

    @GET("search/{type}")
    suspend fun searchType(
        @Path("type") type: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("first_air_date_year") firstAirYear: Int? = null,
    ): Page

    @GET("search/multi")
    suspend fun search(@Query("query") query: String, @Query("include_adult") adult: Boolean = false): Page

    /** include_image_language=null keeps the images with no language, which by TMDB's convention carry no text. */
    @GET("movie/{id}/images")
    suspend fun movieImages(@Path("id") id: Int, @Query("include_image_language") language: String = "null"): MovieImages

    @GET("{type}/{id}/watch/providers")
    suspend fun watchProviders(@Path("type") type: String, @Path("id") id: Int): WatchProviders

    @GET("{type}/{id}/recommendations")
    suspend fun recommendations(@Path("type") type: String, @Path("id") id: Int): Page

    @GET("{type}/{id}/external_ids")
    suspend fun externalIds(@Path("type") type: String, @Path("id") id: Int): ExternalIds

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String): GenreList

    @GET("watch/providers/{type}")
    suspend fun providerCatalog(@Path("type") type: String, @Query("watch_region") region: String): ProviderCatalog

    companion object {
        fun create(key: String): TmdbApi {
            // A v4 read access token is a JWT; anything shorter is a v3 api_key.
            val bearer = key.length > 60
            val http = OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.newBuilder().addQueryParameter("language", "en-US")
                        .apply { if (!bearer) addQueryParameter("api_key", key) }
                        .build()
                    chain.proceed(
                        req.newBuilder().url(url)
                            .apply { if (bearer) header("Authorization", "Bearer $key") }
                            .build(),
                    )
                }
                .build()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }
    }
}

@Serializable
data class Page(val results: List<TmdbMedia> = emptyList())

@Serializable
data class TmdbMedia(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
)

@Serializable
data class MovieImages(val backdrops: List<Backdrop> = emptyList()) {
    @Serializable
    data class Backdrop(
        @SerialName("file_path") val path: String,
        @SerialName("iso_639_1") val language: String? = null,
        @SerialName("vote_count") val votes: Int = 0,
    )
}

@Serializable
data class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
data class GenreList(val genres: List<Genre> = emptyList()) {
    @Serializable
    data class Genre(val id: Int, val name: String)
}

@Serializable
data class Provider(
    @SerialName("provider_id") val id: Int,
    @SerialName("provider_name") val name: String,
)

@Serializable
data class ProviderCatalog(val results: List<Provider> = emptyList())

@Serializable
data class WatchProviders(val results: Map<String, RegionProviders> = emptyMap())

@Serializable
data class RegionProviders(
    val flatrate: List<Provider> = emptyList(),
    val free: List<Provider> = emptyList(),
    val ads: List<Provider> = emptyList(),
)

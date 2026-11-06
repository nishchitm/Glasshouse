package com.cleantv.launcher.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.cleantv.launcher.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Where the TV physically is, from the `home.*` settings. Set rather than detected, because IP geolocation reports a
 * VPN's exit and the TV's network-set time zone can follow it. Without coordinates there's simply no weather.
 */
object HomeLocation {
    val NAME = BuildConfig.HOME_NAME
    val LATITUDE = BuildConfig.HOME_LATITUDE.toDoubleOrNull() ?: 0.0
    val LONGITUDE = BuildConfig.HOME_LONGITUDE.toDoubleOrNull() ?: 0.0
    val hasWeather = BuildConfig.HOME_LATITUDE.toDoubleOrNull() != null && BuildConfig.HOME_LONGITUDE.toDoubleOrNull() != null
    val zone: ZoneId = runCatching { ZoneId.of(BuildConfig.HOME_ZONE) }.getOrDefault(ZoneId.systemDefault())

    /** An optional second, smaller clock ("India:Asia/Kolkata"), or null. */
    val secondClock: Pair<String, ZoneId>? = BuildConfig.SECOND_CLOCK.split(':', limit = 2).takeIf { it.size == 2 }?.let { (label, id) ->
        runCatching { label to ZoneId.of(id) }.getOrNull()
    }
}

data class Weather(
    val temperatureC: Int,
    val icon: String,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val rainChance1h: Int? = null,
    val rainChance2h: Int? = null,
    val aqi: Int? = null, // US AQI scale (0–500)
)

/** Open-Meteo: free, no API key. */
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun current(
        @Query("latitude") latitude: Double = HomeLocation.LATITUDE,
        @Query("longitude") longitude: Double = HomeLocation.LONGITUDE,
        @Query("current") fields: String = "temperature_2m,weather_code,is_day",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("timezone") timezone: String = HomeLocation.zone.id,
        @Query("forecast_days") days: Int = 1,
        @Query("hourly") hourly: String = "precipitation_probability",
        @Query("forecast_hours") hours: Int = 3,
    ): Forecast

    // Same provider, different host.
    @GET("https://air-quality-api.open-meteo.com/v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double = HomeLocation.LATITUDE,
        @Query("longitude") longitude: Double = HomeLocation.LONGITUDE,
        @Query("current") fields: String = "us_aqi",
    ): AirQuality

    companion object {
        fun create(): WeatherApi = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApi::class.java)
    }
}

@Serializable
data class Forecast(val current: Current, val daily: Daily? = null, val hourly: Hourly? = null) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val code: Int,
        @SerialName("is_day") val isDay: Int = 1,
    )

    // Times arrive as local ISO strings in the requested zone, e.g. "2026-11-03T07:21".
    // Starts at the current hour, so [1] and [2] are one and two hours out.
    @Serializable
    data class Hourly(@SerialName("precipitation_probability") val rain: List<Int?> = emptyList())

    @Serializable
    data class Daily(val sunrise: List<String> = emptyList(), val sunset: List<String> = emptyList())

    fun toWeather() = Weather(
        current.temperature.roundToInt(),
        icon(current.code, current.isDay == 1),
        daily?.sunrise?.firstOrNull()?.let { LocalDateTime.parse(it).toLocalTime() },
        daily?.sunset?.firstOrNull()?.let { LocalDateTime.parse(it).toLocalTime() },
        rainChance1h = hourly?.rain?.getOrNull(1),
        rainChance2h = hourly?.rain?.getOrNull(2),
    )
}

@Serializable
data class AirQuality(val current: Current) {
    @Serializable
    data class Current(@SerialName("us_aqi") val usAqi: Int? = null)
}

// WMO weather interpretation codes, as used by Open-Meteo. Monochrome symbols (U+FE0E asks for the text
// glyph, not the colour emoji) so they sit quietly beside the clock.
private fun icon(code: Int, day: Boolean) = when (code) {
    0, 1 -> if (day) "☀" else "☾"
    2, 3 -> "☁"
    45, 48 -> "≋"
    in 51..67, in 80..82 -> "☂"
    in 71..77, 85, 86 -> "❄"
    in 95..99 -> "ϟ"
    else -> "☁"
} + "\uFE0E"

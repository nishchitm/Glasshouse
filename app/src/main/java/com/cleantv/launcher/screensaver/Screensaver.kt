package com.cleantv.launcher.screensaver

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.RailKind
import com.cleantv.launcher.data.weather.HomeLocation
import com.cleantv.launcher.data.weather.Weather
import com.cleantv.launcher.data.weather.WeatherApi
import com.cleantv.launcher.ui.wallpaper.Wallpaper
import com.cleantv.launcher.ui.wallpaper.WallpaperStyle
import com.cleantv.launcher.ui.wallpaper.celestialProgress
import com.cleantv.launcher.ui.wallpaper.skyPalette
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A title to feature, with where it streams for the viewer (if known). */
private data class Feature(val media: Media, val where: String?)

/**
 * The wallpaper with a big clock, an optional second clock and the weather; about every 30 seconds it fades to a title
 * from Latest / For You with where it streams, then back. The clock drifts a little each minute so nothing
 * sits still on the panel.
 */
@Composable
fun Screensaver() {
    val context = LocalContext.current
    val graph = context.applicationContext as CleanTvApplication
    val style = remember {
        val saved = context.getSharedPreferences("home", Context.MODE_PRIVATE).getString("wallpaper", null)
        WallpaperStyle.entries.firstOrNull { it.name == saved } ?: WallpaperStyle.Dunes
    }
    var now by remember { mutableStateOf(LocalTime.now(HomeLocation.zone)) }
    var drift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now(HomeLocation.zone)
            delay(15_000)
            if (now.second < 15) drift++
        }
    }
    val weather by produceState<Weather?>(null) {
        value = runCatching { WeatherApi.create().current().toWeather() }.getOrNull()
    }
    val features by produceState(emptyList<Feature>()) {
        val repo = graph.repo ?: return@produceState
        val latest = runCatching { repo.homeRails(Region.default).firstOrNull { it.kind == RailKind.Latest }?.items }
            .getOrNull().orEmpty().take(10)
        val forYou = runCatching { graph.taste?.recommend(Region.default, limit = 10) }.getOrNull().orEmpty()
        val mixed = (latest.zip(forYou).flatMap { listOf(it.first, it.second) } + latest + forYou).distinctBy { it.key }
        // Where each streams, filled in as it arrives (cached lookups after the first).
        value = mixed.map { Feature(it, null) }
        value = mixed.map { m ->
            Feature(m, runCatching { repo.availability(m, Region.default).services.firstOrNull()?.label }.getOrNull())
        }
    }
    // null = the clock; otherwise the index of the featured title.
    var showing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(features.isNotEmpty()) {
        if (features.isEmpty()) return@LaunchedEffect
        var i = 0
        while (true) {
            delay(30_000)
            showing = i++ % features.size
            delay(12_000)
            showing = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Wallpaper(
            style,
            skyPalette(now, weather?.sunrise, weather?.sunset),
            celestialProgress(now, weather?.sunrise, weather?.sunset),
            Modifier.fillMaxSize(),
        )
        AnimatedContent(
            targetState = showing?.let { features.getOrNull(it) },
            transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(1500)) },
            label = "screensaver",
        ) { feature ->
            if (feature == null) {
                ClockFace(now, weather, drift)
            } else {
                FeatureCard(feature)
            }
        }
    }
}

@Composable
private fun ClockFace(now: LocalTime, weather: Weather?, drift: Int) {
    // A small, slow walk around the lower left so the clock never sits still.
    val dx = listOf(0, 24, 48, 24, 0, -12)[drift % 6]
    val dy = listOf(0, -16, 0, 16, 24, 8)[drift % 6]
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 72.dp, bottom = 72.dp).offset(dx.dp, dy.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.ExtraLight)
            Row {
                Text(
                    LocalDate.now(HomeLocation.zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 18.sp,
                )
                if (weather != null) {
                    Spacer(Modifier.width(18.dp))
                    Text("${weather.icon} ${weather.temperatureC}°", color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            HomeLocation.secondClock?.let { (label, zone) ->
                Text(
                    "$label " + LocalTime.now(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(feature.media.backdropUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 72.dp, bottom = 64.dp)) {
            Text(feature.media.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(feature.media.year, feature.where?.let { "On $it" }).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
            )
        }
    }
}

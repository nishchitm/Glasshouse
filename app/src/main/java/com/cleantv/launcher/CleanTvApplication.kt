package com.cleantv.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import com.cleantv.launcher.data.AppSettings
import com.cleantv.launcher.data.f1.F1
import com.cleantv.launcher.data.NightMode
import com.cleantv.launcher.data.HiddenTitles
import com.cleantv.launcher.data.WatchHistory
import com.cleantv.launcher.data.YouTubeLookup
import com.cleantv.launcher.data.govee.Govee
import com.cleantv.launcher.data.govee.Lights
import com.cleantv.launcher.data.imdb.ImdbRatings
import com.cleantv.launcher.party.GameContent
import com.cleantv.launcher.party.PartyMusic
import com.cleantv.launcher.party.Party
import com.cleantv.launcher.party.PartyMode
import com.cleantv.launcher.remote.PhoneRemote
import fi.iki.elonen.NanoHTTPD
import com.cleantv.launcher.data.jarvis.Gemini
import com.cleantv.launcher.data.jarvis.GeminiApi
import com.cleantv.launcher.data.jarvis.Jarvis
import com.cleantv.launcher.data.jarvis.JarvisVoice
import com.cleantv.launcher.data.wallpaper.PhotoWallpapers
import com.cleantv.launcher.data.tmdb.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.cleantv.launcher.data.links.TitleLinks
import com.cleantv.launcher.data.links.WikidataApi
import com.cleantv.launcher.data.taste.TasteDb
import com.cleantv.launcher.data.taste.TasteEngine
import com.cleantv.launcher.data.tmdb.TmdbApi
import com.cleantv.launcher.data.tmdb.TmdbRepository
import com.cleantv.launcher.ui.common.Notices
import com.cleantv.launcher.launch.AppLauncher
import com.cleantv.launcher.launch.TitlePlayer

/** Shared singletons, so Home and Discover use one TMDB cache and one taste database. */
class CleanTvApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repo: TmdbRepository? by lazy {
        BuildConfig.TMDB_KEY.takeIf { it.isNotBlank() }?.let { TmdbRepository(TmdbApi.create(it)) }
    }
    val taste: TasteEngine? by lazy { repo?.let { TasteEngine(it, TasteDb.create(this).dao()) } }
    val launcher by lazy { AppLauncher(this) }
    val history by lazy { WatchHistory(this) }
    val hidden by lazy { HiddenTitles(this) }
    val notices by lazy { Notices(this) }
    val music by lazy { PartyMusic(this) }
    val youtube by lazy { YouTubeLookup() }
    val player by lazy { TitlePlayer(launcher, TitleLinks(WikidataApi.create()), history, youtube) }
    private val imdb by lazy { ImdbRatings(this) }
    private val gemini: Gemini? by lazy {
        BuildConfig.GEMINI_KEY.takeIf { it.isNotBlank() }?.let { Gemini(GeminiApi.create(it)) }
    }
    val jarvis: Jarvis? by lazy { gemini?.let { g -> repo?.let { Jarvis(g, it) } } }
    val govee: Govee? by lazy { BuildConfig.GOVEE_KEY.takeIf { it.isNotBlank() }?.let { Govee(it) } }
    val party by lazy { Party(GameContent(this, repo), jarvisVoice, appScope) }
    val lights by lazy {
        Lights(govee, appScope) { message -> notices.show(message, long = true) }
    }
    val settings by lazy { AppSettings(this) }
    val nightMode by lazy { NightMode(this) }
    val f1 by lazy { F1() }
    val phoneRemote by lazy { PhoneRemote(this) }
    val partyMode by lazy { PartyMode(lights) }
    val jarvisVoice by lazy { JarvisVoice(this) { nightMode.isNight() } }
    val photos by lazy { PhotoWallpapers() }

    override fun onCreate() {
        super.onCreate()
        runCatching { phoneRemote.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        // The speech engine takes a moment to start; have it ready before Jarvis first answers.
        if (BuildConfig.GEMINI_KEY.isNotBlank()) jarvisVoice
        // Keep the IMDb table fresh; at boot the TV may still be offline, so retry until it works.
        appScope.launch {
            while (true) {
                val ok = runCatching { imdb.refresh() }.isSuccess
                delay(if (ok) 24 * 60 * 60_000L else 60_000L)
            }
        }
    }

    // Images (artwork, photo wallpapers) load with an identifying User-Agent: Wikimedia refuses OkHttp's default.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Glasshouse/0.1 (personal Android TV launcher)")
                            .build(),
                    )
                }
                .build()
        }
        .build()

    /** The title's IMDb rating, or null if it has none (or the table hasn't downloaded yet). */
    suspend fun imdbRating(media: Media): Double? {
        val id = runCatching { repo?.imdbId(media) }.getOrNull() ?: return null
        return imdb.rating(id)
    }
}

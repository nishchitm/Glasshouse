package com.cleantv.launcher.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Availability
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.Rail
import com.cleantv.launcher.data.f1.F1Week
import com.cleantv.launcher.data.govee.Lights
import com.cleantv.launcher.data.jarvis.JarvisAction
import com.cleantv.launcher.data.jarvis.JarvisAnswer
import com.cleantv.launcher.data.jarvis.JarvisContext
import com.cleantv.launcher.data.tmdb.RailKind
import com.cleantv.launcher.ui.common.CardMenuChoice
import com.cleantv.launcher.ui.common.WatchOnChoice
import com.cleantv.launcher.ui.common.releaseLabel
import com.cleantv.launcher.data.weather.HomeLocation
import com.cleantv.launcher.data.weather.Weather
import com.cleantv.launcher.data.weather.WeatherApi
import com.cleantv.launcher.ui.wallpaper.WallpaperStyle
import com.cleantv.launcher.launch.DockApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class FocusedAction(val title: String, val hint: String) {
    Discover("Discover", "Swipe through picks to tune For You."),
    Refresh("Refresh", "Swap For You for a new set of picks."),
}

data class HomeState(
    // Always the first configured region on launch; the others are a per-session check.
    val region: Region = Region.default,
    val loading: Boolean = true,
    val rails: List<Rail> = emptyList(),
    val query: String = "",
    val focused: Media? = null,
    val focusedAction: FocusedAction? = null,
    val availability: Availability? = null,
    val imdbRating: Double? = null,
    val message: String? = null,
    val apps: List<DockApp> = emptyList(), // the app bar, in the user's order
    val allApps: List<DockApp>? = null, // every installed TV app while the picker is open, else null
    val watchOn: WatchOnChoice? = null, // a title on several of the user's services, awaiting a pick
    val cardMenu: CardMenuChoice? = null, // OK held on a card
    val f1: F1Week? = null,
    val weather: Weather? = null,
    val wallpaper: WallpaperStyle = WallpaperStyle.Dunes,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as CleanTvApplication
    private val launcher = graph.launcher
    private val repo = graph.repo
    private val taste = graph.taste
    private val weatherApi = WeatherApi.create()
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val onlyMine get() = graph.settings.onlyMine.value

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    private var homeRails: List<Rail> = emptyList()
    private var personalRails: List<Rail> = emptyList() // For You + watchlist, from the taste engine
    private var railsJob: Job? = null
    private var personalJob: Job? = null
    private var forYouKeys: Set<String> = emptySet()
    private var searchJob: Job? = null
    private var availabilityJob: Job? = null
    private var badgeJob: Job? = null
    private val badgesChecked = mutableSetOf<String>()

    // Apart from HomeState: filling these in as they load shouldn't redraw the whole screen.
    private val _cardServices = MutableStateFlow<Map<String, StreamingService>>(emptyMap())
    val cardServices: StateFlow<Map<String, StreamingService>> = _cardServices

    init {
        val saved = WallpaperStyle.entries.firstOrNull { it.name == prefs.getString(KEY_WALLPAPER, null) }
        if (saved != null) _state.update { it.copy(wallpaper = saved) }
        refreshInstalled()
        loadHome()
        // F1 schedule, results and standings, refreshed through the day.
        viewModelScope.launch {
            while (true) {
                runCatching { graph.f1.week() }.onSuccess { w ->
                    _state.update { it.copy(f1 = w) }
                    publishRails()
                }
                delay(30 * 60_000L)
            }
        }
        // Something opened from Glasshouse (or removed): refresh Continue watching.
        viewModelScope.launch { graph.history.items.drop(1).collect { publishRails() } }
        viewModelScope.launch { graph.hidden.keys.drop(1).collect { publishRails() } }
        // "Only my services" changed (Quick settings): rebuild the rows.
        viewModelScope.launch {
            graph.settings.onlyMine.drop(1).collect {
                loadHome()
                refreshPersonal(fresh = false)
            }
        }
        if (HomeLocation.hasWeather) viewModelScope.launch {
            while (true) {
                // Keep the last reading if a refresh fails, but retry soon: at boot the TV is often still offline.
                val ok = runCatching {
                    // Air quality is a nice-to-have: a failure there shouldn't hide the temperature.
                    val aqi = runCatching { weatherApi.airQuality().current.usAqi }.getOrNull()
                    weatherApi.current().toWeather().copy(aqi = aqi)
                }
                    .onSuccess { w -> _state.update { it.copy(weather = w) } }
                    .isSuccess
                delay(if (ok) 15 * 60_000L else 30_000L)
            }
        }
    }

    fun refreshInstalled() {
        viewModelScope.launch(Dispatchers.Default) {
            val apps = pinned().mapNotNull(launcher::app) // drops apps uninstalled since they were pinned
            _state.update { it.copy(apps = apps) }
        }
    }

    /** Opens the F1 TV app. */
    fun openF1Tv() {
        if (!launcher.launch(StreamingService.F1Tv)) toast("F1 TV isn't installed")
    }

    fun openApp(pkg: String) {
        if (!launcher.launchPackage(pkg)) toast("That app isn't installed any more")
    }

    fun openAppPicker() {
        viewModelScope.launch(Dispatchers.Default) {
            val all = launcher.installedApps()
            _state.update { it.copy(allApps = all) }
        }
    }

    fun closeAppPicker() = _state.update { it.copy(allApps = null) }

    /** Adds [pkg] to the end of the app bar, or removes it if it's already there. */
    fun togglePinned(pkg: String) {
        val current = pinned()
        val next = if (pkg in current) current - pkg else current + pkg
        prefs.edit().putString(KEY_DOCK, next.joinToString(",")).apply()
        refreshInstalled()
    }

    /** Moves [pkg] one place left (-1) or right (+1) on the app bar, and remembers the order. */
    fun moveApp(pkg: String, delta: Int) {
        val apps = state.value.apps.toMutableList()
        val from = apps.indexOfFirst { it.packageName == pkg }
        val to = from + delta
        if (from < 0 || to !in apps.indices) return
        apps.add(to, apps.removeAt(from))
        _state.update { it.copy(apps = apps) }
        // Pinned apps that are currently uninstalled keep their place at the end.
        val shown = apps.map { it.packageName }
        val order = shown + (pinned() - shown.toSet())
        prefs.edit().putString(KEY_DOCK, order.joinToString(",")).apply()
    }

    // Until the user changes it, the app bar is their installed streaming services.
    private fun pinned(): List<String> =
        prefs.getString(KEY_DOCK, null)?.split(',')?.filter { it.isNotBlank() } ?: launcher.defaultDock()

    fun nextWallpaper() {
        val next = WallpaperStyle.entries[(state.value.wallpaper.ordinal + 1) % WallpaperStyle.entries.size]
        _state.update { it.copy(wallpaper = next) }
        prefs.edit().putString(KEY_WALLPAPER, next.name).apply()
    }

    fun toggleRegion() {
        val next = Region.all[(Region.all.indexOf(state.value.region) + 1) % Region.all.size]
        _state.update { it.copy(region = next, availability = null) }
        badgesChecked.clear()
        _cardServices.value = emptyMap()
        loadHome()
        state.value.focused?.let(::onFocus)
    }

    /** ✕ Close search, or Back: clears the search and ends the conversation with Jarvis. */
    fun closeSearch() {
        graph.jarvis?.reset()
        onQueryChange("")
    }

    /** Spoken input (the search bar's mic or the remote's mic button): always for Jarvis. */
    fun onVoice(text: String) = onQueryChange(text, voice = true)

    fun onQueryChange(query: String, voice: Boolean = false) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            graph.jarvisVoice.stop()
            _state.update { it.copy(message = null) }
            publishRails()
            return
        }
        val repo = repo ?: return
        val q = query.trim()
        // Spoken, or a few typed words, is for Jarvis ("funny shows like Panchayat", "open YouTube");
        // a word or two typed is a title search.
        val jarvis = graph.jarvis?.takeIf { voice || q.split(Regex("\\s+")).size >= 3 }
        if (jarvis != null) {
            searchJob = viewModelScope.launch {
                if (!voice) delay(900) // typing: wait for a pause; voice arrives whole
                _state.update { it.copy(message = "Jarvis is thinking…") }
                graph.jarvisVoice.warmUp() // load the voice while Gemini works, so the answer is spoken at once
                val ctx = JarvisContext(
                    region = state.value.region,
                    liked = runCatching { taste?.likedTitles() }.getOrNull().orEmpty(),
                    apps = runCatching { launcher.appNames().map { it.second } }.getOrDefault(emptyList()),
                    scenes = graph.lights.sceneNames(),
                )
                runCatching { jarvis.ask(q, ctx, onlyMine) }
                    .onSuccess { answer ->
                        Log.i("Jarvis", "action=${answer.action} titles=${answer.titles.size}")
                        speak(answer)
                        answer.action?.let(::perform)
                        _state.update {
                            it.copy(
                                rails = if (answer.titles.isEmpty()) it.rails else {
                                    listOf(Rail("Jarvis: $q", answer.titles, tab = "✦ Jarvis", note = answer.reply))
                                },
                                message = if (answer.titles.isEmpty() && answer.action == null) answer.reply.ifBlank {
                                    "Jarvis couldn't find anything for that"
                                } else null,
                            )
                        }
                        // A pure command (lights, apps, sleep) leaves nothing to browse: back to normal.
                        if (answer.titles.isEmpty() && answer.action != null && answer.action !is JarvisAction.Play) {
                            delay(1500)
                            onQueryChange("")
                        }
                    }
                    .onFailure { e ->
                        if (e !is CancellationException) {
                            _state.update { it.copy(message = "Jarvis is busy right now, try again in a moment") }
                            graph.jarvisVoice.say("Sorry, I'm a bit busy right now. Try again in a moment.")
                        }
                    }
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            runCatching { repo.search(q) }
                .onSuccess { results ->
                    _state.update {
                        it.copy(
                            rails = listOf(Rail("Results for “${query.trim()}”", results, tab = "Results")),
                            message = if (results.isEmpty()) "Nothing found for “${query.trim()}”" else null,
                        )
                    }
                }
                .onFailure { e -> if (e !is CancellationException) showError(e) }
        }
    }

    /** Does what Jarvis was asked to do. */
    private fun perform(action: JarvisAction) {
        when (action) {
            is JarvisAction.OpenApp -> {
                val apps = launcher.appNames()
                val app = apps.firstOrNull { it.second.equals(action.name, ignoreCase = true) }
                    ?: apps.firstOrNull { it.second.contains(action.name, ignoreCase = true) }
                if (app == null || !launcher.launchPackage(app.first)) toast("Couldn't find ${action.name}")
            }
            is JarvisAction.Play -> play(action.media)
            is JarvisAction.Lights -> when (action.preset.lowercase()) {
                "on" -> graph.lights.apply(Lights.Preset.On)
                "off" -> graph.lights.apply(Lights.Preset.Off)
                "movie" -> graph.lights.apply(Lights.Preset.Movie)
                "bright" -> graph.lights.apply(Lights.Preset.Bright)
                "gold" -> graph.lights.apply(Lights.Preset.Gold)
                else -> graph.lights.scene(action.preset)
            }
            is JarvisAction.Sleep -> graph.nightMode.setSleepTimer(action.minutes)
        }
    }

    /** Says Jarvis's reply, then the top pick and where it streams ("Top pick: Gullak, on SonyLIV."). */
    private fun speak(answer: JarvisAnswer) {
        val top = answer.titles.firstOrNull()
        val where = answer.topService?.label
        val pick = when {
            top == null -> ""
            where != null -> " Top pick: ${top.title}, on $where."
            else -> " Top pick: ${top.title}."
        }
        graph.jarvisVoice.say(answer.reply + pick)
    }

    /** An action tile (Discover, Refresh) is focused: no title, so the hero explains the tile instead. */
    fun onFocusAction(action: FocusedAction) {
        availabilityJob?.cancel()
        _state.update { it.copy(focused = null, availability = null, focusedAction = action) }
    }

    /**
     * Rebuilds For You and the watchlist, e.g. after returning from Discover with new ratings.
     * @param fresh skip the picks currently showing, so a manual refresh brings a new set.
     */
    fun refreshPersonal(fresh: Boolean = false) {
        val taste = taste ?: return
        val showing = if (fresh) forYouKeys else emptySet()
        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            val region = state.value.region
            val forYou = runCatching { taste.recommend(region, limit = 20, exclude = showing, onlyMine = onlyMine) }.getOrDefault(emptyList())
                // Ran out of new picks: start over rather than show an empty row.
                .ifEmpty { runCatching { taste.recommend(region, limit = 20, onlyMine = onlyMine) }.getOrDefault(emptyList()) }
            forYouKeys = forYou.map { it.key }.toSet()
            val watchlist = taste.watchlist()
            personalRails = listOfNotNull(
                Rail("For You", forYou, RailKind.ForYou),
                watchlist.takeIf { it.isNotEmpty() }?.let { Rail("Your watchlist", it, tab = "Watchlist") },
            )
            publishRails()
        }
    }

    /**
     * A card got focus. Nothing changes until focus rests on it for a moment: scrolling past cards would
     * otherwise swap the full-screen artwork and details for each one, which is what made the TV stutter.
     * Then the title shows, and its rating and services follow together in a single update.
     */
    fun onFocus(media: Media) {
        if (state.value.focused == media) return
        availabilityJob?.cancel()
        availabilityJob = viewModelScope.launch {
            delay(FOCUS_SETTLE_MS)
            _state.update { it.copy(focused = media, focusedAction = null, availability = null, imdbRating = null) }
            val repo = repo ?: return@launch
            val rating = async { graph.imdbRating(media) }
            val availability = async { runCatching { repo.availability(media, state.value.region) }.getOrNull() }
            val a = availability.await()
            val r = rating.await()
            _state.update { if (it.focused == media) it.copy(availability = a, imdbRating = r) else it }
            if (a?.services?.isNotEmpty() == true) graph.player.prefetch(media)
        }
    }

    /** Opens [media] on the first of the user's installed services that carries it, as directly as possible. */
    fun play(media: Media) {
        if (media.upcoming) return toast("${media.title} comes out ${releaseLabel(media.releaseDate!!)}")
        // Continue watching: straight back to the app it was opened in.
        graph.history.items.value.firstOrNull { it.media.key == media.key }?.let { w ->
            viewModelScope.launch { graph.player.playOn(media, w.service)?.let(::toast) }
            return
        }
        val repo = repo ?: return
        viewModelScope.launch {
            // OK can be pressed before the focus-triggered availability lookup has finished.
            val known = state.value.availability?.takeIf { state.value.focused == media }
            val services = (known ?: runCatching { repo.availability(media, state.value.region) }.getOrNull())
                ?.services.orEmpty()
            val installed = graph.player.installed(services)
            if (installed.size > 1) {
                // On several of the viewer's services: let them choose.
                val options = installed.map { it to launcher.appFor(it) }
                _state.update { it.copy(watchOn = WatchOnChoice(media, options)) }
            } else {
                graph.player.play(media, services)?.let(::toast)
            }
        }
    }

    fun openCardMenu(media: Media, inContinue: Boolean) = _state.update { it.copy(cardMenu = CardMenuChoice(media, inContinue)) }

    fun closeCardMenu() = _state.update { it.copy(cardMenu = null) }

    fun removeFromHistory(media: Media) {
        closeCardMenu()
        graph.history.remove(media)
        toast("Removed ${media.title} from Continue")
    }

    /** Off every row for good; Quick settings can bring hidden titles back. */
    fun hideTitle(media: Media) {
        closeCardMenu()
        graph.history.remove(media)
        graph.hidden.hide(media)
        toast("You won't see ${media.title} again")
    }

    /** Finds which service each card on [rail] is on. One rail at a time, as it's shown, so the TV isn't flooded with lookups. */
    fun loadBadges(rail: Rail) {
        val repo = repo ?: return
        badgeJob?.cancel()
        badgeJob = viewModelScope.launch {
            rail.items.filter { it.id > 0 && !it.upcoming && it.key !in badgesChecked && it.key !in _cardServices.value }.chunked(BADGE_BATCH).forEach { batch ->
                val found = batch.map { media ->
                    async { media to runCatching { repo.availability(media, state.value.region) }.getOrNull() }
                }.awaitAll()
                found.filter { it.second != null }.forEach { badgesChecked += it.first.key }
                val services = found.mapNotNull { (media, a) -> a?.services?.firstOrNull()?.let { media.key to it } }.toMap()
                _cardServices.update { services + it }
            }
        }
    }

    fun playOn(media: Media, service: StreamingService) {
        _state.update { it.copy(watchOn = null) }
        viewModelScope.launch { graph.player.playOn(media, service)?.let(::toast) }
    }

    fun closeWatchOn() = _state.update { it.copy(watchOn = null) }

    private fun loadHome() {
        val repo = repo
        if (repo == null) {
            _state.update {
                it.copy(loading = false, message = "Add your TMDB key to local.properties (tmdb.apiKey=…) and rebuild.")
            }
            return
        }
        railsJob?.cancel()
        railsJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            // As the home app, Glasshouse starts with the TV, often before Wi-Fi is up: keep retrying network failures.
            var backoff = 5_000L
            while (true) {
                val result = runCatching { repo.homeRails(state.value.region, onlyMine) }
                result.onSuccess { rails ->
                    homeRails = rails
                    _state.update { it.copy(loading = false, message = null) }
                    publishRails()
                    refreshPersonal() // the network is up now, so For You can fetch too
                    return@launch
                }
                val e = result.exceptionOrNull()!!
                if (e is CancellationException) throw e
                showError(e)
                if (e !is IOException) return@launch // e.g. a rejected key: retrying won't help
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    // Search results own the shelf while a query is active.
    private fun publishRails() {
        // Tab order: Continue watching, For You, Latest, Watchlist, the rest, and Coming soon last.
        val latest = homeRails.filter { it.kind == RailKind.Latest }
        val upcoming = homeRails.filter { it.kind == RailKind.Upcoming }
        val others = homeRails - latest.toSet() - upcoming.toSet()
        val (forYou, watchlist) = personalRails.partition { it.kind == RailKind.ForYou }
        val hidden = graph.hidden.keys.value
        val watched = graph.history.items.value.filter { it.media.key !in hidden }
        // What was actually watched on beats where a title is available.
        _cardServices.update { it + watched.associate { w -> w.media.key to w.service } }
        val continuing = watched.map { it.media }.takeIf { it.isNotEmpty() }
            ?.let { listOf(Rail("Continue watching", it, RailKind.Continue, tab = "Continue")) }.orEmpty()
        val rows = (continuing + forYou + latest + watchlist + others + upcoming).map { rail ->
            if (hidden.isEmpty()) rail else rail.copy(items = rail.items.filter { it.key !in hidden })
        }
        _state.update {
            if (it.query.isNotBlank()) it else it.copy(rails = rows)
        }
    }

    private fun showError(e: Throwable) {
        val msg = when {
            e is retrofit2.HttpException && e.code() == 401 -> "TMDB rejected the API key (401). Check tmdb.apiKey."
            e is IOException -> "Waiting for the network…"
            else -> "Couldn't load from TMDB: ${e.message ?: e::class.simpleName}"
        }
        _state.update { it.copy(loading = false, message = msg) }
    }

    private fun toast(text: String) = graph.notices.show(text)

    private companion object {
        const val KEY_WALLPAPER = "wallpaper"
        const val FOCUS_SETTLE_MS = 180L
        const val KEY_DOCK = "dock"
        const val BADGE_BATCH = 4
    }
}

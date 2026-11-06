package com.cleantv.launcher.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.ui.common.WatchOnChoice
import com.cleantv.launcher.data.taste.Signal
import com.cleantv.launcher.data.tmdb.Availability
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverState(
    val deck: List<Media> = emptyList(),
    val availability: Map<String, Availability> = emptyMap(),
    val genreNames: Map<MediaType, Map<Int, String>> = emptyMap(),
    val imdb: Map<String, Double> = emptyMap(),
    val rated: Int = 0,
    val loading: Boolean = true,
    val message: String? = null,
    val watchOn: WatchOnChoice? = null,
)

class DiscoverViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as CleanTvApplication
    private val repo = graph.repo
    private val taste = graph.taste

    private val region = Region.default

    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state
    private var refillJob: Job? = null

    init {
        if (taste == null) {
            _state.update { it.copy(loading = false, message = "Add your TMDB key to local.properties to use Discover.") }
        } else {
            viewModelScope.launch { _state.update { it.copy(rated = taste.ratedCount()) } }
            viewModelScope.launch {
                val names = MediaType.entries.associateWith { t -> runCatching { repo?.genreNames(t) }.getOrNull().orEmpty() }
                _state.update { it.copy(genreNames = names) }
            }
            refill()
        }
    }

    fun rate(media: Media, signal: Signal) {
        val taste = taste ?: return
        _state.update { s -> s.copy(deck = s.deck.filterNot { it.key == media.key }, rated = s.rated + 1) }
        viewModelScope.launch {
            taste.rate(media, signal)
            if (state.value.deck.size < REFILL_BELOW) refill()
        }
        prefetchAvailability()
    }

    fun play(media: Media) {
        val repo = repo ?: return
        viewModelScope.launch {
            val services = (state.value.availability[media.key] ?: runCatching { repo.availability(media, region) }.getOrNull())
                ?.services.orEmpty()
            val installed = graph.player.installed(services)
            if (installed.size > 1) {
                _state.update { s -> s.copy(watchOn = WatchOnChoice(media, installed.map { it to graph.launcher.appFor(it) })) }
            } else {
                graph.player.play(media, services)?.let(::toast)
            }
        }
    }

    fun playOn(media: Media, service: StreamingService) {
        _state.update { it.copy(watchOn = null) }
        viewModelScope.launch { graph.player.playOn(media, service)?.let(::toast) }
    }

    fun closeWatchOn() = _state.update { it.copy(watchOn = null) }

    private fun toast(text: String) = graph.notices.show(text)

    private fun refill() {
        val taste = taste ?: return
        if (refillJob?.isActive == true) return
        refillJob = viewModelScope.launch {
            val queued = state.value.deck.map { it.key }.toSet()
            runCatching { taste.recommend(region, limit = 30, exclude = queued, onlyMine = graph.settings.onlyMine.value) }
                .onSuccess { more ->
                    _state.update {
                        it.copy(
                            deck = it.deck + more,
                            loading = false,
                            message = if (it.deck.isEmpty() && more.isEmpty()) "You've rated everything we found. Check back later." else null,
                        )
                    }
                    prefetchAvailability()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, message = "Couldn't load picks: ${e.message ?: e::class.simpleName}") }
                }
        }
    }

    // The visible card and the next two, so "Watch on" is ready by the time a card comes up.
    private fun prefetchAvailability() {
        val repo = repo ?: return
        state.value.deck.take(3).filter { it.key !in state.value.availability }.forEach { media ->
            viewModelScope.launch {
                graph.imdbRating(media)?.let { r -> _state.update { it.copy(imdb = it.imdb + (media.key to r)) } }
            }
            viewModelScope.launch {
                runCatching { repo.availability(media, region) }
                    .onSuccess { a ->
                        _state.update { it.copy(availability = it.availability + (media.key to a)) }
                        if (a.services.isNotEmpty()) graph.player.prefetch(media)
                    }
            }
        }
    }

    private companion object {
        const val REFILL_BELOW = 8
    }
}

package com.cleantv.launcher.launch

import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.WatchHistory
import com.cleantv.launcher.data.YouTubeLookup
import com.cleantv.launcher.data.links.TitleLinks
import com.cleantv.launcher.data.tmdb.Media

/** "Play" for a title: pick the user's service that carries it and open it as directly as possible. */
class TitlePlayer(private val launcher: AppLauncher, private val links: TitleLinks, private val history: WatchHistory, private val youtube: YouTubeLookup) {

    /**
     * @param services the user's services carrying [media] in the current region, in preference order.
     * @return a short message for the user when it couldn't go straight to the title, else null.
     */
    suspend fun play(media: Media, services: List<StreamingService>): String? {
        val service = services.firstOrNull(launcher::isInstalled)
            ?: return if (services.isEmpty()) "${media.title} isn't on your services" else
                "Install ${services.first().label} to watch ${media.title}"
        return playOn(media, service)
    }

    /** Which of [services] are installed, in order: more than one means the viewer should choose. */
    fun installed(services: List<StreamingService>) = services.filter(launcher::isInstalled)

    /** Opens [media] on [service] specifically, as directly as it can. */
    suspend fun playOn(media: Media, service: StreamingService): String? {
        // A Continue watching card for an app that didn't say what was playing: just reopen the app.
        if (media.id < 0 && media.title == service.label) {
            if (launcher.launch(service)) history.opened(media, service)
            return null
        }
        // A YouTube video isn't on TMDB or Wikidata: back to the video itself, where the viewer left off.
        if (service == StreamingService.YouTube && media.id < 0) {
            val known = history.items.value.firstOrNull { it.media.key == media.key }?.link
            val video = known ?: youtube.link(media.title, media.overview)
            val seconds = history.position(media) / 1000
            val url = video?.let { if (seconds > 10) "$it&t=${seconds}s" else it }
            val opened = launcher.openTitle(service, url, media.title)
            if (opened != Opened.NotInstalled) history.opened(media, service, video)
            return if (opened == Opened.Title) null else "Searching YouTube for ${media.title}"
        }
        // No link is fine: the launcher falls back to search, then the app's home.
        val url = runCatching { links.forTitle(media)[service] }.getOrNull()
        val opened = launcher.openTitle(service, url, media.title)
        if (opened != Opened.NotInstalled) history.opened(media, service)
        return when (opened) {
            Opened.Title -> null
            Opened.Search -> "Searching ${service.label} for ${media.title}"
            Opened.AppHome -> "Opened ${service.label}: find ${media.title} there"
            Opened.NotInstalled -> "Install ${service.label} to watch ${media.title}"
        }
    }

    /** Warms the link cache while a title is focused, so OK opens it without a pause. */
    suspend fun prefetch(media: Media) {
        runCatching { links.forTitle(media) }
    }
}

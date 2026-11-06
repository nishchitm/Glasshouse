package com.cleantv.launcher.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.cleantv.launcher.data.StreamingService

/** How far [AppLauncher.openTitle] got: the title's own page, the app's search, or only the app. */
enum class Opened { Title, Search, AppHome, NotInstalled }

/** Opens a service's TV app, ideally straight on a title. */
class AppLauncher(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun isInstalled(service: StreamingService) = packageFor(service) != null

    /** The app bar's starting set: the user's streaming services that are installed. */
    fun defaultDock(): List<String> = StreamingService.entries.mapNotNull(::packageFor)

    /** Every app with a TV launcher entry (except Glasshouse itself), by name. */
    fun installedApps(): List<DockApp> = tvApps()
        .map { it.activityInfo.packageName }
        .filter { it != context.packageName }
        .distinct()
        .mapNotNull(::app)
        .sortedBy { it.label.lowercase() }

    /** Every TV app as (package, name), without loading any artwork: for Jarvis. */
    fun appNames(): List<Pair<String, String>> = tvApps()
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != context.packageName }
        .distinctBy { it.first }

    /**
     * An installed app with its own artwork: its TV banner (the 16:9 tile Google TV shows) when it has one,
     * else its icon. Null when it isn't installed.
     */
    fun app(pkg: String): DockApp? {
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val component = pm.getLeanbackLaunchIntentForPackage(pkg)?.component
        val banner = component?.let { runCatching { pm.getActivityBanner(it) }.getOrNull() }
            ?: pm.getApplicationBanner(info)
        val bitmap = banner?.toBitmap(BANNER_W, BANNER_H) ?: pm.getApplicationIcon(info).toBitmap(ICON, ICON)
        return DockApp(pkg, pm.getApplicationLabel(info).toString(), bitmap.asImageBitmap(), isBanner = banner != null)
    }

    /** The installed app for [service] with its artwork, for the "Watch on" picker. */
    fun appFor(service: StreamingService): DockApp? = packageFor(service)?.let(::app)

    fun launchPackage(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** @return false when no app for [service] is installed. */
    fun launch(service: StreamingService): Boolean = packageFor(service)?.let(::launchPackage) ?: false

    /**
     * Tries, in order: [titleUrl] (the title's page on that service), the app's own search for [title],
     * then the app's home. Each link is handed only to the service's app, so a browser never opens.
     *
     * An app can accept a link and still just show its home screen; which ones really jump to the title
     * has to be checked on the TV (scripts/deeplink-probe.sh).
     */
    fun openTitle(service: StreamingService, titleUrl: String?, title: String): Opened {
        val pkg = packageFor(service) ?: return Opened.NotInstalled
        if (titleUrl != null && view(pkg, service, titleUrl)) return Opened.Title
        searchUrl(service, Uri.encode(title))?.let { if (view(pkg, service, it)) return Opened.Search }
        return if (launch(service)) Opened.AppHome else Opened.NotInstalled
    }

    private fun view(pkg: String, service: StreamingService, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Netflix's TV app expects to be told the link came from a launcher.
        if (service == StreamingService.Netflix) intent.putExtra("source", "30")
        if (pm.resolveActivity(intent, 0) == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // Services whose web search URL their TV app is known to accept. (Netflix's TV app only takes
    // /title and /watch links, checked on the test TV.)
    private fun searchUrl(service: StreamingService, query: String) = when (service) {
        StreamingService.YouTube -> "https://www.youtube.com/results?search_query=$query"
        else -> null
    }

    // Known package first; otherwise any TV app whose label contains the service's name.
    private fun packageFor(service: StreamingService): String? {
        val installed = tvApps()
        val packages = installed.map { it.activityInfo.packageName }.toSet()
        service.packages.firstOrNull { it in packages }?.let { return it }
        return installed.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(service.labelHint)
        }?.activityInfo?.packageName
    }

    private fun tvApps() = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
    )

    private companion object {
        // TV banners are 320×180 by spec.
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON = 192
    }
}

data class DockApp(val packageName: String, val label: String, val image: ImageBitmap, val isBanner: Boolean)

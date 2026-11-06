package com.cleantv.launcher.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.govee.Lights
import com.cleantv.launcher.data.weather.HomeLocation
import com.cleantv.launcher.ui.common.NetState
import com.cleantv.launcher.ui.common.TvIcons
import com.cleantv.launcher.ui.common.rememberNetState
import com.cleantv.launcher.ui.glass.DefaultGlass
import com.cleantv.launcher.ui.glass.frostedGlass
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private class QuickSetting(val icon: ImageVector, val label: String, val intent: Intent)

private fun tvSettings(cls: String) =
    Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings$cls"))

/**
 * Stands in for Google TV's quick-settings panel (part of the Google TV home, which is switched off): a sheet
 * sliding in from the right with the time, connection status and a grid of round shortcuts into the TV's own
 * settings. Opened by the ⚙ button and the remote's gear.
 */
@Composable
fun QuickSettingsPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val net by rememberNetState()
    val graph = context.applicationContext as CleanTvApplication
    val scenes by produceState(emptyList<String>()) { value = graph.lights.sceneNames() }
    val items = remember {
        listOf(
            QuickSetting(TvIcons.Picture, "Picture", tvSettings(".device.displaysound.DisplaySoundActivity")),
            QuickSetting(TvIcons.Sound, "Sound", Intent(Settings.ACTION_SOUND_SETTINGS)),
            QuickSetting(TvIcons.Wifi, "Wi-Fi", Intent(Settings.ACTION_WIFI_SETTINGS)),
            QuickSetting(TvIcons.Bluetooth, "Bluetooth", tvSettings(".accessories.AddAccessoryActivity")),
            QuickSetting(TvIcons.Moon, "Screensaver", Intent(Settings.ACTION_DREAM_SETTINGS)),
            QuickSetting(TvIcons.Power, "Screen off", Intent("com.google.android.pano.action.SLEEP")),
            QuickSetting(TvIcons.Accessibility, "Accessibility", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            // Sony's System software update screen (firmware), else Android's.
            QuickSetting(
                TvIcons.Update, "Updates",
                Intent("com.sony.dtv.intent.action.SYSTEM_UPDATE_SETTINGS").takeIf {
                    it.resolveActivity(context.packageManager) != null
                } ?: Intent("android.settings.SYSTEM_UPDATE_SETTINGS"),
            ),
            QuickSetting(TvIcons.Settings, "Settings", Intent(Settings.ACTION_SETTINGS)),
        ).filter { it.intent.resolveActivity(context.packageManager) != null }
    }
    val first = remember { FocusRequester() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A pop-up under the ⚙ button, only as tall as its contents.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            AnimatedVisibility(shown, enter = slideInVertically(tween(200)) { -it / 8 } + fadeIn(tween(200))) {
                Column(
                    Modifier
                        .padding(top = 72.dp, end = 40.dp)
                        .width(300.dp)
                        .frostedGlass(DefaultGlass.copy(tintAlpha = 0.65f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                ) {
                    Header(net)
                    // Android can switch the remote-button service off (e.g. on a force-stop); say so, with the fix.
                    if (!remoteButtonsOn(context)) {
                        SetupBanner("Remote buttons are off. Turn on \"Glasshouse remote buttons\" →") {
                            open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            onClose()
                        }
                    }
                    // Continue watching only sees what plays in other apps with this switched on.
                    if (!mediaWatcherOn(context)) {
                        SetupBanner("For Continue watching, allow \"Glasshouse Continue watching\" →") {
                            open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            onClose()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.chunked(4).forEachIndexed { row, chunk ->
                            Row(Modifier.fillMaxWidth()) {
                                chunk.forEachIndexed { col, item ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                        QuickTile(item, if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier) {
                                            open(context, item.intent)
                                            onClose()
                                        }
                                    }
                                }
                                repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val onlyMine by graph.settings.onlyMine.collectAsState()
                    val night by graph.nightMode.enabled.collectAsState()
                    val sleepAt by graph.nightMode.sleepAt.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LightChip(if (onlyMine) "✓ My services" else "All services", Modifier.weight(1f)) {
                            graph.settings.setOnlyMine(!onlyMine)
                        }
                        LightChip(if (night) "Night: auto" else "Night: off", Modifier.weight(1f)) {
                            graph.nightMode.setEnabled(!night)
                        }
                        val minutesLeft = sleepAt?.let { ((it - System.currentTimeMillis() + 59_999) / 60_000).toInt() }
                        LightChip(if (minutesLeft != null) "Sleep: $minutesLeft min" else "Sleep: off", Modifier.weight(1f)) {
                            graph.nightMode.cycleSleepTimer()
                        }
                    }
                    val hidden by graph.hidden.keys.collectAsState()
                    if (hidden.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LightChip("Bring back ${hidden.size} hidden title${if (hidden.size == 1) "" else "s"}", Modifier.fillMaxWidth()) { graph.hidden.clear() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Film data from TMDB, not endorsed or certified by TMDB. Ratings from IMDb. Weather from Open-Meteo.",
                        color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp,
                    )
                    // Phone remote: the address and PIN to pair a phone.
                    val remoteUrl = remember { phoneRemoteUrl(context) }
                    if (remoteUrl != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Phone remote: $remoteUrl  ·  PIN ${graph.phoneRemote.pin}",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                        )
                    }
                    if (graph.lights.available) {
                        Spacer(Modifier.height(10.dp))
                        Text("Lights", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        val presets = listOf(
                            "All on" to { graph.lights.apply(Lights.Preset.On) },
                            "All off" to { graph.lights.apply(Lights.Preset.Off) },
                            "Movie" to { graph.lights.apply(Lights.Preset.Movie) },
                            "Bright" to { graph.lights.apply(Lights.Preset.Bright) },
                        ) + scenes.map { name -> name.removeSuffix(" setup") to { graph.lights.scene(name) } }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.chunked(4).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { (label, action) ->
                                        LightChip(label, Modifier.weight(1f)) { action() }
                                    }
                                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(net: NetState) {
    val now = LocalDateTime.now(HomeLocation.zone)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(10.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        val offline = net == NetState.Offline
        Image(
            TvIcons.Wifi, null, Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(if (offline) Color(0xFFFF6B6B) else Color(0xFF7BD88F)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            when (net) {
                NetState.Wifi -> "Wi-Fi"
                NetState.Ethernet -> "Cable"
                NetState.Offline -> "Offline"
            },
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
    }
}

/** A round icon with its label underneath, like Google TV's quick settings. */
@Composable
private fun QuickTile(item: QuickSetting, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(38.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    item.icon, item.label, Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(if (focused) Color.Black else Color.White),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A light preset; stays open so you can try a few. */
@Composable
private fun LightChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(label, modifier = Modifier.align(Alignment.Center), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The phone remote's address on the home Wi-Fi (not the VPN tunnel), e.g. "192.168.1.20:8081". */
@Suppress("DEPRECATION")
private fun phoneRemoteUrl(context: Context): String? {
    val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
    val ip = cm.allNetworks.asSequence()
        .filter { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@filter false
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        .flatMap { cm.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
        .map { it.address }
        .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    return ip?.let { "$it:${com.cleantv.launcher.remote.PhoneRemote.PORT}" }
}

private fun remoteButtonsOn(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        .orEmpty().contains("${context.packageName}/")

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun mediaWatcherOn(context: android.content.Context): Boolean =
    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.contains(context.packageName) == true

@Composable
private fun SetupBanner(text: String, onClick: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFB020),
            focusedContainerColor = Color(0xFFFFC94D),
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp)
    }
}

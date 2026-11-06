package com.cleantv.launcher.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class NetState { Wifi, Ethernet, Offline }

/** The TV's connection, live: Wi-Fi, cable, or none (no network, or one without working internet). */
@Composable
fun rememberNetState(): State<NetState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NetState.Wifi) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun read(caps: NetworkCapabilities?) = when {
            caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetState.Offline
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState.Ethernet
            else -> NetState.Wifi
        }
        state.value = read(cm.getNetworkCapabilities(cm.activeNetwork))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = read(caps)
            }

            override fun onLost(network: Network) {
                state.value = read(cm.getNetworkCapabilities(cm.activeNetwork))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return state
}

/** Wi-Fi icon, sized to sit level with 12sp text; struck through in red when offline. */
@Composable
fun WifiGlyph(state: NetState, modifier: Modifier = Modifier) {
    val color = if (state == NetState.Offline) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.75f)
    Box(modifier.size(13.dp)) {
        Image(TvIcons.Wifi, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(color))
        if (state == NetState.Offline) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

package com.cleantv.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.party.GameNightScreen
import com.cleantv.launcher.party.NowPlayingPill
import com.cleantv.launcher.party.rememberNowPlaying
import com.cleantv.launcher.ui.common.NoticeHost
import com.cleantv.launcher.ui.discover.DiscoverScreen
import com.cleantv.launcher.ui.home.HomeScreen
import java.time.LocalTime

private enum class Screen { Home, Discover, GameNight }

@Composable
fun CleanTvApp(
    debugTime: LocalTime? = null,
    quickSettings: Boolean = false,
    jarvisRequests: Int = 0,
    homeRequests: Int = 0,
    remoteText: Triple<Int, String, Boolean>? = null,
    onQuickSettings: (Boolean) -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    // The panel, and Jarvis from the remote's mic button, always open over Home.
    if (quickSettings) screen = Screen.Home
    LaunchedEffect(jarvisRequests) { if (jarvisRequests > 0) screen = Screen.Home }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) screen = Screen.Home }
    LaunchedEffect(remoteText) { if (remoteText != null) screen = Screen.Home }
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                onOpenDiscover = { screen = Screen.Discover },
                onOpenGameNight = { screen = Screen.GameNight },
                quickSettings = quickSettings,
                jarvisRequests = jarvisRequests,
                homeRequests = homeRequests,
                remoteText = remoteText,
                onQuickSettings = onQuickSettings,
                debugTime = debugTime,
            )
            Screen.Discover -> DiscoverScreen(onExit = { screen = Screen.Home })
            Screen.GameNight -> GameNightScreen(onExit = { screen = Screen.Home })
        }
        // Screens change under a quick fade rather than cutting across.
        val veil = remember { Animatable(0f) }
        var firstScreen by remember { mutableStateOf(true) }
        LaunchedEffect(screen) {
            if (firstScreen) {
                firstScreen = false
            } else {
                veil.snapTo(1f)
                veil.animateTo(0f, tween(500))
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(Color.Black))
        val graph = LocalContext.current.applicationContext as CleanTvApplication
        NoticeHost(graph.notices)
        // Party mode: the song on the home screen too, between the clock and the buttons.
        val partyOn by graph.partyMode.on.collectAsState()
        if (partyOn && screen == Screen.Home) {
            NowPlayingPill(rememberNowPlaying(graph.music), Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 290.dp))
        }
    }
}

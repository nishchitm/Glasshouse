package com.cleantv.launcher.ui.home

import com.cleantv.launcher.ui.common.rememberNetState
import com.cleantv.launcher.ui.common.VoiceSearch
import com.cleantv.launcher.ui.common.WifiGlyph
import com.cleantv.launcher.ui.common.rememberVoiceSearch
import com.cleantv.launcher.ui.common.NetState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.data.Region
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Availability
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.data.tmdb.MediaType
import com.cleantv.launcher.data.tmdb.Rail
import com.cleantv.launcher.data.tmdb.RailKind
import com.cleantv.launcher.data.weather.HomeLocation
import com.cleantv.launcher.data.f1.Driver
import com.cleantv.launcher.data.f1.F1Week
import com.cleantv.launcher.data.f1.Fight
import com.cleantv.launcher.data.f1.Session
import com.cleantv.launcher.data.weather.Weather
import com.cleantv.launcher.launch.DockApp
import com.cleantv.launcher.ui.common.AvailabilityRow
import com.cleantv.launcher.ui.common.MetaLine
import com.cleantv.launcher.ui.common.CardMenu
import com.cleantv.launcher.ui.common.WatchOnPicker
import com.cleantv.launcher.ui.common.fadingEdges
import com.cleantv.launcher.ui.common.releaseLabel
import com.cleantv.launcher.ui.glass.DefaultGlass
import com.cleantv.launcher.ui.glass.frostedGlass
import com.cleantv.launcher.ui.wallpaper.Wallpaper
import com.cleantv.launcher.ui.wallpaper.WallpaperStyle
import com.cleantv.launcher.ui.wallpaper.celestialProgress
import com.cleantv.launcher.ui.wallpaper.skyPalette
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** @param debugTime debug builds only: pretend it's this time, to preview the wallpaper's day/night cycle. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    onOpenDiscover: () -> Unit,
    onOpenGameNight: () -> Unit,
    quickSettings: Boolean,
    jarvisRequests: Int,
    homeRequests: Int,
    remoteText: Triple<Int, String, Boolean>?,
    onQuickSettings: (Boolean) -> Unit,
    vm: HomeViewModel = viewModel(),
    debugTime: LocalTime? = null,
) {
    val onOpenQuickSettings = { onQuickSettings(true) }
    // Typed on the phone remote: ask Jarvis, or search.
    LaunchedEffect(remoteText) {
        remoteText?.let { (_, text, jarvis) -> if (jarvis) vm.onVoice(text) else vm.onQueryChange(text) }
    }
    val graph = LocalContext.current.applicationContext as CleanTvApplication
    val partyOn by graph.partyMode.on.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val cardServices by vm.cardServices.collectAsStateWithLifecycle()
    // Read by each card as it draws, so a label arriving redraws that card and not the whole screen.
    val badge = remember { { media: Media -> cardServices[media.key] } }
    val firstCard = remember { FocusRequester() }
    val firstApp = remember { FocusRequester() }
    // Focus goes to the first card again when search is cleared (its results, which held focus, vanish).
    var focusFirstCard by remember { mutableStateOf(false) }
    // The wallpaper shows unless the user is browsing the rows; then the focused title's artwork takes over.
    var browsing by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(debugTime ?: LocalTime.now(HomeLocation.zone)) }
    LaunchedEffect(debugTime) {
        while (true) {
            now = debugTime ?: LocalTime.now(HomeLocation.zone)
            delay(15_000)
        }
    }

    // Apps can be installed while Glasshouse is in the background.
    LifecycleResumeEffect(Unit) {
        vm.refreshInstalled()
        onPauseOrDispose {}
    }
    BackHandler(enabled = state.query.isNotEmpty()) {
        vm.closeSearch()
        focusFirstCard = true
    }
    // Home pressed while already here: back to the plain home screen.
    LaunchedEffect(homeRequests) {
        if (homeRequests > 0 && state.query.isNotEmpty()) {
            vm.closeSearch()
            focusFirstCard = true
        }
    }
    // Launch on the dock, like a TV home screen, so the wallpaper is what you see first.
    // Apps load in the background, and with none installed (e.g. on an emulator) the dock is empty: start
    // on the first card instead. Wait for window focus: when the window gains it, Android focuses the first
    // focusable (the search button), which would override an earlier request.
    var initialFocusDone by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, state.apps.isNotEmpty(), state.rails.isNotEmpty()) {
        if (initialFocusDone || !windowFocused) return@LaunchedEffect
        withFrameNanos { } // let the window's own initial focus land first
        initialFocusDone = when {
            state.apps.isNotEmpty() -> runCatching { firstApp.requestFocus() }.isSuccess
            state.rails.isNotEmpty() -> runCatching { firstCard.requestFocus() }.isSuccess
            else -> false
        }
    }
    // Each time Home is shown (including back from Discover), pick up new ratings.
    LaunchedEffect(Unit) { vm.refreshPersonal() }
    LaunchedEffect(state.rails, focusFirstCard) {
        if (focusFirstCard && state.rails.isNotEmpty()) {
            focusFirstCard = !runCatching { firstCard.requestFocus() }.isSuccess
        }
    }

    // F1 TV focused on the dock brings up the F1 panel. Only this yes/no is kept, so moving between other apps
    // doesn't recompose the home screen.
    var f1Focused by remember { mutableStateOf(false) }
    var f1PanelFocused by remember { mutableStateOf(false) }
    val f1Tile = remember { FocusRequester() }
    val f1Tabs = remember { FocusRequester() }
    // The first app has no focus target while the dock is scrolled past it, and asking for it then would crash.
    val firstAppComposed = remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Background(
            artworkUrl = state.focused?.backdropUrl?.takeIf { browsing },
            wallpaper = if (partyOn) WallpaperStyle.Diwali else state.wallpaper,
            now = now,
            sunrise = state.weather?.sunrise,
            sunset = state.weather?.sunset,
            modifier = Modifier.fillMaxSize(),
        )

        // When the window first gets focus, Android focuses the first focusable (the search button). Point
        // that entry at the app bar instead, once it has loaded.
        val appsLoaded = state.apps.isNotEmpty()
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties {
                    enter = {
                        when {
                            f1PanelFocused -> f1Tile
                            appsLoaded && firstAppComposed.value -> firstApp
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup()
                .padding(horizontal = 48.dp, vertical = 20.dp),
        ) {
            TopBar(
                now, state.weather, state.query, vm::onQueryChange,
                state.wallpaper.label, vm::nextWallpaper, state.region, vm::toggleRegion, onOpenQuickSettings, onOpenGameNight,
                jarvisRequests, vm::onVoice,
            )
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.weight(1f))
            if (browsing || state.message != null) {
                Hero(state.focused, state.focusedAction, state.imdbRating, state.availability, state.region.label, state.message, state.loading)
                Spacer(Modifier.height(14.dp))
            }
            // A session about to start, or live: one press to F1 TV.
            state.f1?.upcoming(withinMinutes = 60)?.let { session ->
                if (!browsing && !f1Focused && !f1PanelFocused) {
                    F1Banner(session, vm::openF1Tv)
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (state.rails.isNotEmpty()) {
                Shelf(
                    state.rails, firstCard, vm::onFocus, vm::play, onOpenDiscover, { vm.refreshPersonal(fresh = true) },
                    vm::onFocusAction,
                    onCloseSearch = if (state.query.isNotEmpty()) {
                        {
                            vm.closeSearch()
                            focusFirstCard = true
                        }
                    } else null,
                    onCardsFocused = { browsing = it },
                    onCardMenu = vm::openCardMenu,
                    onRailShown = vm::loadBadges,
                    badge = badge,
                )
                Spacer(Modifier.height(10.dp))
            }
            Dock(state.apps, firstApp, vm::openApp, vm::openAppPicker, vm::moveApp, f1Tile, f1Tabs.takeIf { state.f1 != null }, firstAppComposed) {
                f1Focused = it in StreamingService.F1Tv.packages
                // A row can swap under the focus (e.g. Continue watching arriving) without reporting focus lost.
                if (it != null) browsing = false
            }
        }
        AnimatedVisibility(
            visible = state.f1 != null && (f1Focused || f1PanelFocused),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 48.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 12 },
            exit = fadeOut(tween(120)),
        ) {
            state.f1?.let { F1Panel(it, f1Tabs, f1Tile, f1PanelFocused) { inside -> f1PanelFocused = inside } }
        }
        // Night mode: the whole home screen a little darker.
        if (graph.nightMode.isNight(now)) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        if (quickSettings) QuickSettingsPanel(onClose = { onQuickSettings(false) })
        state.watchOn?.let { choice -> WatchOnPicker(choice, vm::playOn, vm::closeWatchOn) }
        state.cardMenu?.let { choice -> CardMenu(choice, vm::removeFromHistory, vm::hideTitle, vm::closeCardMenu) }
        state.allApps?.let { all ->
            AppPicker(all, state.apps.map { it.packageName }.toSet(), vm::togglePinned, vm::closeAppPicker)
        }
    }
}

/** The day/night wallpaper, with the focused title's artwork faded over it while browsing the rows. */
@Composable
private fun Background(
    artworkUrl: String?,
    wallpaper: WallpaperStyle,
    now: LocalTime,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    modifier: Modifier,
) {
    // The test TV's GPU is the bottleneck here (measured ~25 ms a frame while browsing), so: each layer renders
    // into its own cached texture (Offscreen) and is only redrawn when it changes; the wallpaper fades out
    // under the artwork so it can be skipped entirely; and the scrims cover only the area they darken.
    val wallpaperAlpha by animateFloatAsState(if (artworkUrl == null) 1f else 0f, tween(400), label = "wallpaper")
    Box(modifier) {
        if (wallpaperAlpha > 0f) {
            Wallpaper(
                wallpaper,
                skyPalette(now, sunrise, sunset),
                celestialProgress(now, sunrise, sunset),
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = wallpaperAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                },
                animate = artworkUrl == null,
            )
        }
        Crossfade(artworkUrl, animationSpec = tween(400), label = "artwork") { url ->
            if (url != null) {
                // ModulateAlpha, not Offscreen: a new offscreen buffer for every artwork change cost ~150 ms frames.
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                    AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(0.65f).background(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)),
                        ),
                    )
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.65f).background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                        ),
                    )
                }
            }
        }
        // Keeps the clock and weather readable over a bright sky or bright artwork.
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.25f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(
    now: LocalTime,
    weather: Weather?,
    query: String,
    onQuery: (String) -> Unit,
    wallpaperLabel: String,
    onNextWallpaper: () -> Unit,
    region: Region,
    onToggleRegion: () -> Unit,
    onOpenQuickSettings: () -> Unit,
    onOpenGameNight: () -> Unit,
    jarvisRequests: Int,
    onVoice: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Clock(now, weather)
        Spacer(Modifier.weight(1f))
        // Right-aligned so expanding buttons grow leftwards and the others stay put. Left/right stop at the
        // ends of the group; otherwise the focus search drops down to the dock.
        val voice = rememberVoiceSearch(onVoice)
        // The remote's mic button: listen straight away.
        LaunchedEffect(jarvisRequests) { if (jarvisRequests > 0) voice.start() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(query, onQuery, voice, Modifier.focusProperties { left = FocusRequester.Cancel })
            IconButton(wallpaperLabel, onNextWallpaper) { Text("◐", fontSize = 13.sp) }
            IconButton(region.label, onToggleRegion) {
                Text(region.code, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(com.cleantv.launcher.party.PartyName.title, onOpenGameNight) { Text("🎉", fontSize = 13.sp) }
            IconButton("Settings", onOpenQuickSettings, Modifier.focusProperties { right = FocusRequester.Cancel }) {
                Text("⚙\uFE0E", fontSize = 14.sp)
            }
        }
    }
}

/** Big thin clock, a hairline, then today's weather and date for the home. */
@Composable
private fun Clock(now: LocalTime, weather: Weather?) {
    val dim = Color.White.copy(alpha = 0.62f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.35f)))
        Spacer(Modifier.width(16.dp))
        Column {
            if (weather != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${weather.icon} ${weather.temperatureC}°", color = Color.White, fontSize = 17.sp)
                    // Only worth a glance when rain is likely in the next couple of hours.
                    val rain = listOfNotNull(weather.rainChance1h, weather.rainChance2h).maxOrNull()
                    if (rain != null && rain >= 30) {
                        Spacer(Modifier.width(14.dp))
                        Text("☂\uFE0E $rain%", color = dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    if (weather.aqi != null) {
                        Spacer(Modifier.width(14.dp))
                        Text("AQI ", color = dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                        Text(
                            weather.aqi.toString(),
                            color = aqiColor(weather.aqi),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            val net by rememberNetState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now(HomeLocation.zone).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) +
                        HomeLocation.NAME.takeIf { it.isNotEmpty() }?.let { "  ·  $it" }.orEmpty(),
                    color = dim,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                )
                HomeLocation.secondClock?.let { (label, zone) ->
                    Text(
                        "  ·  $label " + LocalTime.now(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
                        color = dim,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                WifiGlyph(net)
                if (net == NetState.Offline) {
                    Spacer(Modifier.width(5.dp))
                    Text("No internet", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
        }
    }
}

// US AQI bands: good, moderate, unhealthy for sensitive groups, unhealthy and worse.
private fun aqiColor(aqi: Int) = when {
    aqi <= 50 -> Color(0xFF7BD88F)
    aqi <= 100 -> Color(0xFFF2C94C)
    aqi <= 150 -> Color(0xFFF29A4C)
    else -> Color(0xFFFF6B6B)
}

private val TopButtonSize = 36.dp

/** A round glass button showing just [icon]; focusing it slides out [label], tvOS-style. */
@Composable
private fun IconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(TopButtonSize)
            .defaultMinSize(minWidth = TopButtonSize)
            .frostedGlass(DefaultGlass, CircleShape)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.fillMaxHeight().animateContentSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            if (focused) {
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// One search bar: a round button that opens into a field with the mic at its end. Selecting the field is what
// starts typing (focusing a text field on TV pops the keyboard at once, which would happen every time focus
// passed through the top bar); the mic listens instead.
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, voice: VoiceSearch, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val field = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var groupFocused by remember { mutableStateOf(false) }
    LaunchedEffect(editing) { if (editing) field.requestFocus() }
    val open = groupFocused || editing || query.isNotEmpty() || voice.listening
    val width by animateDpAsState(if (open) 260.dp else TopButtonSize, label = "searchWidth")

    Row(
        modifier
            .size(width = width, height = TopButtonSize)
            .frostedGlass(DefaultGlass, CircleShape)
            .background(if (open) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .onFocusChanged { groupFocused = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { editing = true },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.14f),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchGlyph(Color.White)
                if (open) {
                    Spacer(Modifier.width(10.dp))
                    Box {
                        if (voice.listening) {
                            Text(voice.partial.ifEmpty { "Listening…" }, color = Color.White, fontSize = 12.sp, maxLines = 1)
                        } else if (query.isEmpty()) {
                            Text("Search, or ask Jarvis", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(field)
                                .focusProperties { canFocus = editing }
                                .onFocusChanged { if (!it.isFocused && editing) editing = false }
                                // A text field keeps D-pad up/down for itself; on TV they should leave the field.
                                .onPreviewKeyEvent {
                                    val dir = when (it.key) {
                                        Key.DirectionDown -> FocusDirection.Down
                                        Key.DirectionUp -> FocusDirection.Up
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    if (it.type == KeyEventType.KeyDown) focusManager.moveFocus(dir)
                                    true
                                },
                        )
                    }
                }
            }
        }
        if (open) {
            Surface(
                onClick = voice.start,
                modifier = Modifier.padding(3.dp).size(TopButtonSize - 6.dp),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (voice.listening) Color(0xFFFF5A5F) else Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MicGlyph(LocalContentColor.current) }
            }
        }
    }
}

/** Microphone, drawn like the search glyph. */
@Composable
private fun MicGlyph(color: Color = Color.White) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        drawRoundRect(
            color, topLeft = Offset(w * 0.32f, 0f), size = Size(w * 0.36f, size.height * 0.62f),
            cornerRadius = CornerRadius(w * 0.18f),
        )
        drawArc(
            color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.15f, size.height * 0.2f), size = Size(w * 0.7f, size.height * 0.55f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2, size.height * 0.75f), Offset(w / 2, size.height), stroke, StrokeCap.Round)
    }
}

/** Magnifying glass, drawn so it doesn't depend on the TV's font having a search symbol. */
@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension * 0.33f
        val c = Offset(r + stroke, r + stroke)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.72f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

@Composable
private fun Hero(
    focused: Media?,
    action: FocusedAction?,
    imdbRating: Double?,
    availability: Availability?,
    regionLabel: String,
    message: String?,
    loading: Boolean,
) {
    Column(Modifier.width(440.dp)) {
        when {
            message != null -> Text(message, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            focused == null && loading -> Text("Loading…", color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)
            focused == null && action != null -> {
                Text(action.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(action.hint, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            focused == null -> Unit
            else -> {
                Text(
                    focused.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(focused, imdbRating)
                Spacer(Modifier.height(4.dp))
                Text(
                    focused.overview,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                if (focused.upcoming) {
                    Text("Coming out ${releaseLabel(focused.releaseDate!!)}", color = Color(0xFFFFD27A), fontSize = 12.sp)
                } else {
                    AvailabilityRow(availability, regionLabel)
                }
                Spacer(Modifier.height(4.dp))
                Text("Hold OK to remove", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            }
        }
    }
}


/**
 * The rows as tabs (For You, Latest, Coming soon, …) over a single row of cards, all on one glass panel:
 * the app bar is always one step below the cards. Moving onto a tab switches to it, tvOS-style.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shelf(
    rails: List<Rail>,
    firstCard: FocusRequester,
    onFocus: (Media) -> Unit,
    onPlay: (Media) -> Unit,
    onOpenDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onFocusAction: (FocusedAction) -> Unit,
    onCloseSearch: (() -> Unit)?,
    onCardsFocused: (Boolean) -> Unit,
    onCardMenu: (Media, Boolean) -> Unit,
    onRailShown: (Rail) -> Unit,
    badge: (Media) -> StreamingService?,
    modifier: Modifier = Modifier,
) {
    // Remembered by name, so For You arriving after the other tabs doesn't shift the selection.
    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rails.indexOfFirst { it.title == selectedTitle }.takeIf { it >= 0 } ?: 0
    val rail = rails[selected]
    LaunchedEffect(rail.title, rail.items.size) { onRailShown(rail) }
    val tabRequesters = remember(rails.size) { List(rails.size) { FocusRequester() } }

    Column(
        modifier
            .fillMaxWidth()
            .frostedGlass(PanelGlass, PanelShape)
            .padding(vertical = 10.dp),
    ) {
        val tabsState = rememberLazyListState()
        LazyRow(
            Modifier
                .fillMaxWidth()
                .fadingEdges(tabsState)
                // Coming up from the cards lands on the open tab, not whichever tab sits above.
                .focusProperties { enter = { tabRequesters[selected] } }
                .focusGroup(),
            state = tabsState,
            contentPadding = PaddingValues(horizontal = PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onCloseSearch != null) {
                // Search results take over the panel; this is the visible way back (Back also works).
                item(key = "close") { CloseChip(onCloseSearch) }
            }
            itemsIndexed(rails, key = { _, r -> r.title }) { i, r ->
                Tab(r.tab, i == selected, Modifier.focusRequester(tabRequesters[i])) { selectedTitle = r.title }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Slide the new row in from the side of the tab you moved to, fading the old one out. The title's artwork
        // and details show only while a card itself has focus, not the tabs above.
        Box(Modifier.onFocusChanged { onCardsFocused(it.hasFocus) }) {
        AnimatedContent(
            targetState = selected to rail,
            transitionSpec = {
                val dir = if (targetState.first >= initialState.first) 1 else -1
                (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                    .using(SizeTransform(clip = false))
            },
            contentKey = { it.second.title },
            label = "rail",
        ) { (_, shown) ->
            Column {
                shown.note?.let {
                    Text(
                        "✦  $it",
                        modifier = Modifier.padding(start = PanelPadding, end = PanelPadding, bottom = 2.dp),
                        color = Color(0xFFCBB8FF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RailRow(shown, firstCard, onFocus, onPlay, onOpenDiscover, onRefresh, onFocusAction, onCardMenu, badge)
            }
        }
        }
    }
}

private val PanelShape = RoundedCornerShape(22.dp)
private val PanelPadding = 16.dp
// Lighter than the default glass: enough to lift the panel off the wallpaper without hiding it.
private val PanelGlass = DefaultGlass.copy(tintAlpha = 0.2f, rim = 0.4f)

@Composable
private fun CloseChip(onClose: () -> Unit) {
    Surface(
        onClick = onClose,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text("✕  Close search", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A tab label; focusing it selects it. */
@Composable
private fun Tab(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        modifier = modifier.onFocusChanged { if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.9f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun RailRow(
    rail: Rail,
    firstCard: FocusRequester?,
    onFocus: (Media) -> Unit,
    onPlay: (Media) -> Unit,
    onOpenDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onFocusAction: (FocusedAction) -> Unit,
    onCardMenu: (Media, Boolean) -> Unit,
    badge: (Media) -> StreamingService?,
) {
    val leadsWithDiscover = rail.kind == RailKind.ForYou
    // The rail's first focus target: the Discover tile when present, else the first title.
    fun Modifier.first(isFirst: Boolean) = if (isFirst && firstCard != null) focusRequester(firstCard) else this
    val rowState = rememberLazyListState()
    // A card taken off the row via its menu: focus moves to the one that takes its place, not off the row.
    var heldIndex by remember { mutableStateOf<Int?>(null) }
    val replacement = remember { FocusRequester() }
    var lastSize by remember { mutableStateOf(rail.items.size) }
    LaunchedEffect(rail.items.size) {
        if (rail.items.size < lastSize && heldIndex != null && rail.items.isNotEmpty()) {
            withFrameNanos { }
            runCatching { replacement.requestFocus() }
            heldIndex = null
        }
        lastSize = rail.items.size
    }
    Column {
        LazyRow(
            Modifier.fadingEdges(rowState),
            state = rowState,
            // Vertical room for the focused card's grow animation inside the panel.
            contentPadding = PaddingValues(horizontal = PanelPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leadsWithDiscover) {
                // Two half-height buttons stacked in one narrow slot.
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(ActionGap)) {
                        ActionTile(
                            "✦", onOpenDiscover,
                            Modifier.first(true).onFocusChanged { if (it.isFocused) onFocusAction(FocusedAction.Discover) },
                        )
                        ActionTile(
                            "↻", onRefresh,
                            Modifier.onFocusChanged { if (it.isFocused) onFocusAction(FocusedAction.Refresh) },
                        )
                    }
                }
            }
            itemsIndexed(rail.items, key = { _, m -> m.key }) { i, media ->
                MediaCard(
                    media,
                    onClick = { onPlay(media) },
                    onLongClick = {
                        heldIndex = i
                        onCardMenu(media, rail.kind == RailKind.Continue)
                    },
                    badge = badge(media),
                    modifier = Modifier
                        .first(i == 0 && !leadsWithDiscover)
                        .then(if (i == heldIndex?.coerceAtMost(rail.items.lastIndex)) Modifier.focusRequester(replacement) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocus(media) },
                )
            }
        }
    }
}

private val ScreenGutter = 48.dp
private val CardWidth = 136.dp
private val CardHeight = 76.dp
private val CardShape = RoundedCornerShape(14.dp)
private val RailHeaderHeight = 22.dp

// Room above for the label and around the card for its focus scale.
private val ShelfTopPadding = 6.dp
private val ShelfHeight = ShelfTopPadding + RailHeaderHeight + CardHeight + 12.dp

@OptIn(ExperimentalFoundationApi::class)
private val NoVerticalScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Lets a child span [gutter] past its parent's horizontal padding on both sides, to reach the screen edges. */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

private val ActionGap = 6.dp

/** A small round icon button leading the For You rail: ✦ Discover, ↻ Refresh (named in the details area). */
@Composable
private fun ActionTile(icon: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size((CardHeight - ActionGap) / 2),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text(icon, modifier = Modifier.align(Alignment.Center), fontSize = 15.sp)
    }
}

// Artwork with the title small in the corner; the focused title's full details show above the row.
@Composable
private fun MediaCard(media: Media, onClick: () -> Unit, modifier: Modifier, onLongClick: (() -> Unit)? = null, badge: StreamingService? = null) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width = CardWidth, height = CardHeight),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), shape = CardShape),
        ),
    ) {
        AsyncImage(
            media.backdropUrl.replace("/w1280", "/w500"),
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )
        Text(
            media.title,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 5.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The service it's on, small enough not to compete with the artwork.
        if (badge != null) {
            Text(
                badge.label,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private val AppTileWidth = 104.dp
private val AppTileHeight = 58.dp

/** The app bar: the user's pinned apps as their own TV banners, then a tile to add or remove apps. */
@Composable
private fun Dock(
    apps: List<DockApp>,
    firstApp: FocusRequester,
    onOpen: (String) -> Unit,
    onEdit: () -> Unit,
    onMove: (String, Int) -> Unit,
    f1Tile: FocusRequester,
    f1Tabs: FocusRequester?,
    firstAppComposed: MutableState<Boolean>,
    onAppFocused: (String?) -> Unit,
) {
    // Hold OK on an app to pick it up; ◀ ▶ then move it, and OK or Back puts it down.
    var moving by remember { mutableStateOf<String?>(null) }
    val okReleased = remember { mutableStateOf(false) }
    Box {
    // The apps load after the + tile is shown; when they arrive, start from the first one. Otherwise the row
    // stays anchored on +, the first app is scrolled off (so it can't take the initial focus).
    val listState = rememberLazyListState()
    LaunchedEffect(apps.firstOrNull()?.packageName) { listState.scrollToItem(0) }
    LazyRow(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.hasFocus) onAppFocused(null) }
            .frostedGlass(PanelGlass, PanelShape)
            .fadingEdges(listState),
        state = listState,
        contentPadding = PaddingValues(horizontal = PanelPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
            val isMoving = moving == app.packageName
            if (i == 0) {
                DisposableEffect(Unit) {
                    firstAppComposed.value = true
                    onDispose { firstAppComposed.value = false }
                }
            }
            AppTile(
                app,
                Modifier
                    .animateItem()
                    .onFocusChanged { if (it.isFocused) onAppFocused(app.packageName) }
                    // Up from F1 TV goes to the tabs of its panel.
                    .then(
                        if (f1Tabs != null && app.packageName in StreamingService.F1Tv.packages) {
                            Modifier.focusRequester(f1Tile).focusProperties { up = f1Tabs }
                        } else Modifier,
                    )
                    // While one app is picked up, the rest step back.
                    .graphicsLayer { alpha = if (moving != null && !isMoving) 0.45f else 1f }
                    .then(if (i == 0) Modifier.focusRequester(firstApp) else Modifier)
                    .then(if (isMoving) Modifier.onPreviewKeyEvent { e -> moveKeys(e, app.packageName, okReleased, onMove) { moving = null } } else Modifier),
                moving = isMoving,
                onLongClick = {
                    okReleased.value = false
                    moving = app.packageName
                },
            ) { if (isMoving) moving = null else onOpen(app.packageName) }
        }
        item(key = "edit") {
            Surface(
                onClick = onEdit,
                modifier = Modifier
                    .then(if (apps.isEmpty()) Modifier.focusRequester(firstApp) else Modifier)
                    .size(width = AppTileHeight, height = AppTileHeight),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            ) {
                Text("+", modifier = Modifier.align(Alignment.Center), fontSize = 24.sp, fontWeight = FontWeight.Light)
            }
        }
    }
    if (moving != null) {
        Text(
            "◀ ▶  move   ·   OK  place",
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18).dp),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
        )
    }
    }
}

/** An app's banner (or icon and name); [checked] marks it as on the app bar, in the picker. */
@Composable
internal fun AppTile(
    app: DockApp,
    modifier: Modifier,
    checked: Boolean = false,
    moving: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // Picked up (tvOS-style): lifted a little and gently wiggling.
    val wiggle = if (moving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            -1.2f, 1.2f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "angle",
        ).value
    } else 0f
    val lift by animateFloatAsState(if (moving) 1.08f else 1f, label = "lift")
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .graphicsLayer {
                rotationZ = wiggle
                scaleX = lift
                scaleY = lift
            }
            .size(width = AppTileWidth, height = AppTileHeight),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape),
        ),
    ) {
        if (app.isBanner) {
            Image(app.image, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // No banner: the app's icon on a frosted tile, with its name.
            Column(
                Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(app.image, null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    app.label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (checked) {
            Text(
                "✓",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 5.dp),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Every installed TV app; OK adds it to the app bar or takes it off. A dialog, so focus stays inside. */
@Composable
private fun AppPicker(all: List<DockApp>, pinned: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = ScreenGutter, vertical = 32.dp),
        ) {
            Text("Apps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "OK adds an app to the app bar or removes it  ·  Back closes",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(AppTileWidth),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(all, key = { _, a -> a.packageName }) { i, app ->
                    AppTile(
                        app,
                        if (i == 0) Modifier.focusRequester(first) else Modifier,
                        checked = app.packageName in pinned,
                    ) { onToggle(app.packageName) }
                }
            }
        }
    }
}

/** While an app is picked up: ◀ ▶ move it, OK or Back drop it. Everything else is swallowed. */
private fun moveKeys(e: androidx.compose.ui.input.key.KeyEvent, pkg: String, released: MutableState<Boolean>, onMove: (String, Int) -> Unit, onDrop: () -> Unit): Boolean {
    // The app is picked up while OK is still held, and the remote keeps repeating it until it's let go. So OK only
    // puts the app down on a release after that one, which also stops the release opening the app.
    if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
        if (e.type == KeyEventType.KeyUp) {
            if (released.value) onDrop() else released.value = true
        }
        return true
    }
    if (e.type != KeyEventType.KeyDown) return e.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Back)
    when (e.key) {
        Key.DirectionLeft -> onMove(pkg, -1)
        Key.DirectionRight -> onMove(pkg, 1)
        Key.Back -> onDrop()
        Key.DirectionUp, Key.DirectionDown -> Unit // stay on the app bar while moving
        else -> return false
    }
    return true
}

// ---------- F1

private val F1Red = Color(0xFFE10600)

/** Shown while F1 TV is focused on the dock. Up from F1 TV reaches the tabs: what's next, the F1 schedule, the F2 calendar. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun F1Panel(week: F1Week, tabs: FocusRequester, tile: FocusRequester, inside: Boolean, onInside: (Boolean) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    BackHandler(enabled = inside) { runCatching { tile.requestFocus() } }
    Column(
        Modifier
            .width(340.dp)
            .frostedGlass(DefaultGlass.copy(tintAlpha = 0.78f), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier.onFocusChanged { onInside(it.hasFocus) },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            F1_TABS.forEachIndexed { i, label ->
                Tab(
                    label,
                    tab == i,
                    Modifier
                        .then(if (i == 0) Modifier.focusRequester(tabs) else Modifier)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = tile
                            if (i == 0) left = FocusRequester.Cancel
                            if (i == F1_TABS.lastIndex) right = FocusRequester.Cancel
                        },
                ) { tab = i }
            }
            Spacer(Modifier.weight(1f))
            if (!inside) Text("▲ tabs", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().height(156.dp)) {
            when (tab) {
                0 -> F1NextTab(week)
                1 -> F1ScheduleTab(week)
                else -> F2CalendarTab(week)
            }
        }
    }
}

private val F1_TABS = listOf("Next", "F1", "F2")

@Composable
private fun F1NextTab(week: F1Week) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        week.next?.let { race ->
            val session = week.nextSession()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${countryFlag(race.place)}  ${race.name.replace("Grand Prix", "GP")}",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (session != null) Text("Next: ${session.name}", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
                }
                if (session != null) {
                    Text(
                        if (session.isLive()) "LIVE" else timeUntil(session),
                        color = if (session.isLive()) F1Red else Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1,
                    )
                }
            }
        }
        week.fight?.let { F1Fight(it) }
        week.podium.firstOrNull()?.let { winner ->
            Text(
                "🏆  ${winner.surname} won in ${week.lastRace.orEmpty().replace(" Grand Prix", "")}",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** This weekend's sessions, then the races after it as small cards. */
@Composable
private fun ColumnScope.F1ScheduleTab(week: F1Week) {
    week.next?.let { race ->
        WeekendHeader(countryFlag(race.place), race.name.replace("Grand Prix", "GP"), weekendDates(race.sessions))
        WeekendSessions(race.sessions, lightNext = true)
    }
    if (week.later.isNotEmpty()) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.later.forEach { race ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${countryFlag(race.place)} ${race.name.replace(" Grand Prix", "")}",
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(raceDate(race.race), color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun F2CalendarTab(week: F1Week) {
    if (week.f2.isEmpty()) {
        Text("No upcoming F2 rounds this season", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
    week.f2.forEachIndexed { i, round ->
        if (i > 0) Spacer(Modifier.height(6.dp))
        WeekendHeader(countryFlag(round.country), round.place, weekendDates(round.sessions))
        WeekendSessions(round.sessions, lightNext = i == 0)
    }
}

@Composable
private fun WeekendHeader(flag: String, name: String, detail: String) {
    Row(Modifier.padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$flag  $name", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** One line a session: day, name, local time, second clock if there is one. The next one is lit up, finished ones fade. */
@Composable
private fun WeekendSessions(sessions: List<Session>, lightNext: Boolean) {
    val now = java.time.Instant.now()
    val upNext = sessions.firstOrNull { it.isLive(now) || it.start > now }.takeIf { lightNext }
    sessions.forEach { s ->
        val next = s == upNext
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (s.start < now && !next) 0.4f else 1f }
                .background(if (next) F1Red.copy(alpha = 0.9f) else Color.Transparent, RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(zoned(s, "EEE"), color = Color.White.copy(alpha = if (next) 0.9f else 0.55f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
            Text(s.name, color = Color.White, fontSize = 11.sp, fontWeight = if (next) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (s.timeKnown) zoned(s, "HH:mm") else raceDate(s), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val second = HomeLocation.secondClock?.second
            if (s.timeKnown && second != null) {
                Text(
                    s.start.atZone(second).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White.copy(alpha = if (next) 0.85f else 0.5f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(38.dp),
                )
            }
        }
    }
}

private fun zoned(s: Session, pattern: String): String =
    s.start.atZone(HomeLocation.zone).format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

private fun raceDate(s: Session): String = zoned(s, "d MMM")

/** "6 – 8 Nov" for a weekend's first and last sessions. */
private fun weekendDates(sessions: List<Session>): String {
    val first = sessions.firstOrNull() ?: return ""
    val last = sessions.last()
    val sameMonth = zoned(first, "MMM") == zoned(last, "MMM")
    return "${zoned(first, if (sameMonth) "d" else "d MMM")} – ${raceDate(last)}"
}

/** Two drivers face to face in their team colours, with the gap between them. */
@Composable
private fun F1Fight(fight: Fight) {
    val colors = listOf(Color(0xCC000000 or fight.ahead.driver.color.toLong()), Color(0xCC000000 or fight.behind.driver.color.toLong()))
    Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(colors))) {
        DriverPhoto(fight.ahead.driver, Modifier.align(Alignment.BottomStart))
        DriverPhoto(fight.behind.driver, Modifier.align(Alignment.BottomEnd))
        Column(Modifier.align(Alignment.Center).width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fight.label, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(if (fight.gap == 1) "1 point" else "${fight.gap} points", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                "${fight.ahead.driver.surname} ${fight.ahead.points}  ·  ${fight.behind.driver.surname} ${fight.behind.points}",
                color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(fight.note, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DriverPhoto(driver: Driver, modifier: Modifier) {
    if (driver.headshot == null) return
    AsyncImage(model = driver.headshot, contentDescription = driver.surname, modifier = modifier.size(80.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
}

/** The flag for a "City, Country" place, from the country's ISO code. */
private fun countryFlag(place: String): String {
    val iso = F1Countries[place.substringAfterLast(", ")] ?: return "🏁"
    return iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

private val F1Countries = mapOf(
    "Australia" to "AU", "China" to "CN", "Japan" to "JP", "Bahrain" to "BH", "Saudi Arabia" to "SA", "USA" to "US",
    "United States" to "US", "Italy" to "IT", "Monaco" to "MC", "Spain" to "ES", "Canada" to "CA", "Austria" to "AT",
    "UK" to "GB", "United Kingdom" to "GB", "Hungary" to "HU", "Belgium" to "BE", "Netherlands" to "NL",
    "Azerbaijan" to "AZ", "Singapore" to "SG", "Mexico" to "MX", "Brazil" to "BR", "Qatar" to "QA", "UAE" to "AE",
    "United Arab Emirates" to "AE",
)

/** "🏁 Qualifying starts in 25 min" or "🔴 Race is live", above the rows; OK opens F1 TV. */
@Composable
private fun F1Banner(session: Session, onClick: () -> Unit) {
    val live = session.isLive()
    val minutes = java.time.Duration.between(java.time.Instant.now(), session.start).toMinutes().coerceAtLeast(0)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = F1Red.copy(alpha = 0.75f),
            focusedContainerColor = F1Red,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(
            if (live) "🔴  F1 ${session.name} is live  ·  OK to watch on F1 TV" else "🏁  F1 ${session.name} starts in $minutes min  ·  OK to watch",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun timeUntil(s: Session): String {
    val d = java.time.Duration.between(java.time.Instant.now(), s.start)
    val days = d.toDays()
    val hours = d.minusDays(days).toHours()
    return if (days > 0) "${days}d ${hours}h" else "${hours}h ${d.toMinutesPart()}m"
}

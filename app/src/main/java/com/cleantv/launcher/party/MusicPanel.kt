package com.cleantv.launcher.party

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The party's jukebox: what's playing, the buttons, and playlists to start. */
@Composable
internal fun MusicPanel(music: PartyMusic, onMessage: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var now by remember { mutableStateOf<PartyMusic.NowPlaying?>(null) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        LaunchedEffect(Unit) {
            while (true) {
                now = withContext(Dispatchers.IO) { music.now() }
                delay(1_200)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), Alignment.Center) {
            Column(Modifier.width(430.dp).background(Paper, CardShape), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().background(Plum, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(vertical = 9.dp), Alignment.Center) {
                    Text("🎵  THE JUKEBOX", color = Marigold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val song = now
                    Text(
                        song?.title ?: "Nothing playing yet", color = Plum, fontSize = 18.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song?.artist?.ifEmpty { null } ?: "Pick a playlist below", color = Plum.copy(alpha = 0.7f), fontSize = 13.sp,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JukeButton("⏮", Modifier.weight(1f)) { music.previous() }
                        JukeButton(if (song?.playing == true) "⏸  Pause" else "▶  Play", Modifier.weight(2f).focusRequester(first)) { music.togglePlay() }
                        JukeButton("⏭", Modifier.weight(1f)) { music.next() }
                    }
                    Spacer(Modifier.height(12.dp))
                    music.playlists.forEach { playlist ->
                        JukeButton("♫  ${playlist.label}", Modifier.fillMaxWidth()) {
                            onClose()
                            if (music.play(playlist)) onMessage("Starting ${playlist.label}. Back to the game in a moment") else onMessage("Spotify isn't installed")
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    JukeButton("Pick songs in YouTube Music", Modifier.fillMaxWidth()) {
                        onClose()
                        if (!music.openYouTubeMusic()) onMessage("YouTube Music isn't installed")
                    }
                }
            }
        }
    }
}

@Composable
private fun JukeButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Plum.copy(alpha = 0.12f),
            focusedContainerColor = Plum,
            contentColor = Plum,
            focusedContentColor = Marigold,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(label, Modifier.align(Alignment.Center), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

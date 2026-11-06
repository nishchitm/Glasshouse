package com.cleantv.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cleantv.launcher.data.StreamingService
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.launch.DockApp
import com.cleantv.launcher.ui.glass.DefaultGlass
import com.cleantv.launcher.ui.glass.frostedGlass
import com.cleantv.launcher.ui.home.AppTile

/** A title on several of the viewer's services, waiting for them to pick one; each option has its app's art. */
data class WatchOnChoice(val media: Media, val options: List<Pair<StreamingService, DockApp?>>)

/** "Watch on": a small pop-up with each service that carries the title, as its own app banner. */
@Composable
fun WatchOnPicker(choice: WatchOnChoice, onPick: (Media, StreamingService) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .frostedGlass(DefaultGlass.copy(tintAlpha = 0.65f), RoundedCornerShape(22.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Watch ${choice.media.title} on", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    choice.options.forEachIndexed { i, (service, app) ->
                        val modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
                        if (app != null) {
                            AppTile(app, modifier) { onPick(choice.media, service) }
                        } else {
                            Surface(onClick = { onPick(choice.media, service) }, modifier = modifier) {
                                Text(service.label, Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

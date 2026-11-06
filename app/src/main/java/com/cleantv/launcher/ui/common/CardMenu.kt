package com.cleantv.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cleantv.launcher.data.tmdb.Media
import com.cleantv.launcher.ui.glass.DefaultGlass
import com.cleantv.launcher.ui.glass.frostedGlass

/** Hold OK on a card: what can be done with it. [inContinue] when it was held on the Continue watching row. */
data class CardMenuChoice(val media: Media, val inContinue: Boolean)

@Composable
fun CardMenu(choice: CardMenuChoice, onRemove: (Media) -> Unit, onHide: (Media) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        // The menu opens while OK is still held, and letting go would press its first option. So OK does nothing
        // until that release has passed, or until a fresh press if the release went elsewhere.
        var armed by remember { mutableStateOf(false) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).onPreviewKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    armed || !ok -> false
                    event.type == KeyEventType.KeyUp -> { armed = true; true }
                    event.nativeKeyEvent.repeatCount > 0 -> true
                    else -> { armed = true; false }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(300.dp)
                    .frostedGlass(DefaultGlass.copy(tintAlpha = 0.65f), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(choice.media.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                if (choice.inContinue) {
                    MenuButton("Remove from Continue", Modifier.focusRequester(first)) { onRemove(choice.media) }
                    MenuButton("Never show this again") { onHide(choice.media) }
                } else {
                    MenuButton("Don't show this again", Modifier.focusRequester(first)) { onHide(choice.media) }
                }
                MenuButton("Cancel", onClick = onClose)
            }
        }
    }
}

@Composable
private fun MenuButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(label, Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

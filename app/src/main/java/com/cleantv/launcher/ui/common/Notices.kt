package com.cleantv.launcher.ui.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One short message; [id] restarts the timer when the same words are shown twice. */
data class Notice(val text: String, val millis: Long, val id: Long = System.nanoTime())

/**
 * Short messages drawn by Glasshouse itself. Android's own toast is only for when another app is on screen,
 * where Glasshouse can't draw.
 */
class Notices(private val context: Context) {
    private val _current = MutableStateFlow<Notice?>(null)
    val current: StateFlow<Notice?> = _current

    /** Set by MainActivity while it's on screen. */
    @Volatile
    var foreground = false

    fun show(text: String, long: Boolean = false) {
        if (foreground) {
            _current.value = Notice(text, if (long) LONG_MS else SHORT_MS)
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val SHORT_MS = 2_500L
        const val LONG_MS = 4_500L
    }
}

/** Fades the current notice in near the bottom of the screen. */
@Composable
fun BoxScope.NoticeHost(notices: Notices) {
    val notice by notices.current.collectAsState()
    var shown by remember { mutableStateOf(false) }
    // Kept while fading out, so the words don't vanish before the pill does.
    var text by remember { mutableStateOf("") }
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        text = current.text
        shown = true
        delay(current.millis)
        shown = false
    }
    AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .background(Color(0xE6141418), NoticeShape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), NoticeShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(text, color = Color.White, fontSize = 13.sp)
        }
    }
}

private val NoticeShape = RoundedCornerShape(16.dp)

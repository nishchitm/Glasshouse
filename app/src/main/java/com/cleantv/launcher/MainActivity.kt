package com.cleantv.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.cleantv.launcher.ui.CleanTvApp
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val quickSettings = mutableStateOf(false)
    // Bumped each time the remote's mic button asks for Jarvis; Home starts listening on each change.
    private val jarvisRequests = mutableIntStateOf(0)
    // Bumped when Home is pressed while Glasshouse is already open: back to the plain home screen.
    private val homeRequests = mutableIntStateOf(0)
    // Text from the phone remote: (counter, text, for Jarvis?). The counter makes repeats count.
    private val remoteText = mutableStateOf<Triple<Int, String, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // While this is the home app, Back on the root screen must not exit to nothing. Registered first,
        // so Compose BackHandlers (e.g. clearing search) still take precedence.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        // Debug only: `adb shell am start -n com.cleantv.launcher/.MainActivity --es debug_time 06:45` previews the
        // wallpaper at that time.
        val debugTime = intent.getStringExtra("debug_time")
            ?.takeIf { BuildConfig.DEBUG }
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (BuildConfig.DEBUG) {
            PerfFlags.staticWallpaper = intent.getBooleanExtra("perf_static_wallpaper", false)
            PerfFlags.noBlur = intent.getBooleanExtra("perf_no_blur", false)
        }
        handle(intent)
        setContent { CleanTvApp(debugTime, quickSettings.value, jarvisRequests.intValue, homeRequests.intValue, remoteText.value) { quickSettings.value = it } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            quickSettings.value = false
            homeRequests.intValue++
        }
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_SETTINGS, false) == true) quickSettings.value = true
        if (intent?.getBooleanExtra(EXTRA_JARVIS, false) == true) jarvisRequests.intValue++
        intent?.getStringExtra(EXTRA_ASK)?.let { remoteText.value = Triple((remoteText.value?.first ?: 0) + 1, it, true) }
        intent?.getStringExtra(EXTRA_SEARCH)?.let { remoteText.value = Triple((remoteText.value?.first ?: 0) + 1, it, false) }
        // Auditioning Jarvis voices: adb shell am start -n com.cleantv.launcher/.MainActivity --es jarvis_voice <name>
        intent?.getStringExtra("jarvis_voice")?.let { (application as CleanTvApplication).jarvisVoice.use(it) }
    }

    // The remote's gear (NOTIFICATION on the test TV, SETTINGS on others) while Glasshouse is in front. Elsewhere,
    // RemoteKeyService sends it here with EXTRA_QUICK_SETTINGS.
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            quickSettings.value = true
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_QUICK_SETTINGS = "quick_settings"
        const val EXTRA_JARVIS = "jarvis"
        const val EXTRA_ASK = "ask"
        const val EXTRA_SEARCH = "search"
    }

    override fun onResume() {
        super.onResume()
        (application as CleanTvApplication).notices.foreground = true
    }

    override fun onPause() {
        (application as CleanTvApplication).notices.foreground = false
        super.onPause()
    }
}

package com.cleantv.launcher.remote

import com.cleantv.launcher.BuildConfig
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.cleantv.launcher.MainActivity
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Remaps remote buttons system-wide. Accessibility services see key presses before apps (and before Sony's
 * own button handling), which is the only way a launcher can act on a button while another app is in front.
 * Keycodes are the ones a Sony Bravia remote sends, read off the device; others may differ.
 */
class RemoteKeyService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action: () -> Unit = when (event.keyCode) {
            // The gear button. Google TV's home answered it with its quick-settings panel; with that home turned
            // off, Glasshouse shows its own.
            KeyEvent.KEYCODE_NOTIFICATION -> { ->
                startActivity(
                    Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK_SETTINGS, true).newTask(),
                )
            }
            // A remote's app shortcut (the Disney+ button on some Sony remotes) → the app in `remote.button9.app`.
            KeyEvent.KEYCODE_BUTTON_9 -> BuildConfig.REMOTE_BUTTON_APP.takeIf { it.isNotEmpty() }?.let { app -> { -> openApp(listOf(app)) } } ?: return false
            // The mic / Google Assistant button → Jarvis, listening.
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_SEARCH -> { ->
                startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_JARVIS, true).newTask())
            }
            else -> return false
        }
        // Swallow both halves of the press so the original action never fires; act once, on release.
        if (event.action == KeyEvent.ACTION_UP) action()
        return true
    }

    private fun openApp(packages: List<String>) {
        val pm = packageManager
        val intent = packages.firstNotNullOfOrNull { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) }
        intent?.let { startActivity(it.newTask()) }
    }

    private val volumes by lazy { AppVolumes(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == GOOGLE_VOICE) return watchGoogle()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg == packageName) volumes.onHome() else volumes.onForeground(pkg)
    }

    /**
     * The mic button hands the remote's audio to Google's voice panel and nobody else, so Google has to do the
     * listening. This reads what it heard off the panel; once the words settle, the panel is closed and they go to
     * Jarvis, before Google acts on them. Nothing else on the screen is read.
     */
    private fun watchGoogle() {
        if (SystemClock.uptimeMillis() < ignoreGoogleUntil) return
        if (!watching) {
            watching = true
            heard = ""
            serviceInfo = serviceInfo.apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                notificationTimeout = 100
            }
        }
        handler.removeCallbacks(stopWatching)
        handler.postDelayed(stopWatching, 30_000)
        val words = windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName == GOOGLE_VOICE }?.let(::spokenWords) }
        if (!words.isNullOrBlank() && words != heard) {
            heard = words
            handler.removeCallbacks(toJarvis)
            handler.postDelayed(toJarvis, WORDS_SETTLED_MS)
        }
    }

    private fun spokenWords(node: AccessibilityNodeInfo): String? {
        if (node.viewIdResourceName?.endsWith("/user_query") == true) return node.text?.toString()
        return (0 until node.childCount).firstNotNullOfOrNull { i -> node.getChild(i)?.let(::spokenWords) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private var heard = ""
    private var ignoreGoogleUntil = 0L
    private val stopWatching = Runnable {
        watching = false
        serviceInfo = serviceInfo.apply { eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
    }
    private val toJarvis = Runnable {
        val words = heard
        handler.removeCallbacks(stopWatching)
        stopWatching.run()
        ignoreGoogleUntil = SystemClock.uptimeMillis() + 3_000 // its panel closing isn't a new press
        closeGoogle(words, attempts = 6)
    }

    /** One Back only stops Google listening and leaves its panel up, so keep going until the panel has gone. */
    private fun closeGoogle(words: String, attempts: Int) {
        if (attempts > 0 && windows.any { it.root?.packageName == GOOGLE_VOICE }) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ closeGoogle(words, attempts - 1) }, 250)
        } else {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ASK, words).newTask())
        }
    }

    override fun onInterrupt() = Unit

    private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        /** The running service, for the phone remote's Home/Back buttons; null while it's switched off. */
        @Volatile var instance: RemoteKeyService? = null
            private set

        // Google ends its own listening under a second after the last word.
        private const val WORDS_SETTLED_MS = 800L
        private const val GOOGLE_VOICE = "com.google.android.katniss"
    }
}

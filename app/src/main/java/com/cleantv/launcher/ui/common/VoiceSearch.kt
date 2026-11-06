package com.cleantv.launcher.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cleantv.launcher.CleanTvApplication
import com.cleantv.launcher.remote.RemoteKeyService

/** In-app voice input: [start] listens once; [listening] and [partial] drive the search box meanwhile. */
class VoiceSearch internal constructor(val start: () -> Unit) {
    var listening by mutableStateOf(false)
        internal set
    var partial by mutableStateOf("")
        internal set
}

/**
 * Listens with Android's SpeechRecognizer (Google's recognizer on the TV) rather than the system voice screen,
 * which on Google TV recognises speech but never returns the words to the calling app.
 */
@Composable
fun rememberVoiceSearch(onResult: (String) -> Unit): VoiceSearch {
    val context = LocalContext.current
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    lateinit var voice: VoiceSearch
    var loudest = -100f

    fun listen() {
        // This TV's only mic is in the remote, and its audio only reaches Google's panel, which RemoteKeyService reads.
        if (RemoteKeyService.instance != null &&
            runCatching { context.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        ) {
            return
        }
        voice.partial = ""
        voice.listening = true
        loudest = -100f
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen()
    }
    voice = remember {
        VoiceSearch {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) listen() else askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(results: Bundle) {
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { voice.partial = it }
            }

            override fun onResults(results: Bundle) {
                voice.listening = false
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(onResult)
            }

            override fun onError(error: Int) {
                voice.listening = false
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    (context.applicationContext as CleanTvApplication).notices.show("Didn't hear anything. Press the mic and try again")
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > loudest) loudest = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer.destroy() }
    }
    return voice
}

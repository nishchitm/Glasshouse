package com.cleantv.launcher.data.jarvis

import android.content.Context
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/** Jarvis's spoken replies, through the TV's text-to-speech (Google's engine on the test TV). */
class JarvisVoice(context: Context, private val quiet: () -> Boolean = { false }) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val duck = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .build()
    private val prefs = context.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private var ready = false
    private var lastText = ""
    private var fellBack = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech
        ready = true
        // US English as the base: the engine's plain en-IN voice fails to load on the test TV and swallows speech
        // without reporting an error (checked 2026-11-03). Its online en-IN voices work; one is chosen below.
        tts.language = Locale.US
        tts.setSpeechRate(1.15f)
        // Plain media audio: some TVs route or mute TTS's default "assistant" usage.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // The chosen voice (see use()); by default an Indian English male online voice, picked on the TV.
        val name = prefs.getString(KEY_VOICE, null) ?: DEFAULT_VOICE
        tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
        Log.i(TAG, "ready, voice=${tts.voice?.name}")

        // Anything asked to be said while the engine was still starting.
        pending?.let { say(it) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                Log.i(TAG, "spoke (${tts.voice?.name})")
                audio.abandonAudioFocusRequest(duck)
            }

            // If the voice fails, fall back to the engine's default once and say it again.
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = fallBack()

            override fun onError(utteranceId: String?, errorCode: Int) = fallBack()
        })
    }

    private fun fallBack() {
        audio.abandonAudioFocusRequest(duck)
        Log.w(TAG, "voice ${tts.voice?.name} failed")
        if (fellBack) return
        fellBack = true
        tts.language = Locale.US // offline, e.g. when the online voice can't be reached
        say(lastText)
    }

    fun say(text: String) {
        lastText = text
        if (!ready) {
            pending = text
            return
        }
        pending = null
        // Night mode: half volume.
        val params = Bundle().apply { if (quiet()) putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.5f) }
        // Lets party music dip while Jarvis talks, so Tambola calls can be heard over it.
        audio.requestAudioFocus(duck)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis")
    }

    /**
     * Loads the voice ahead of time. On the test TV the engine drops idle voices under memory pressure and
     * reloading takes about a second, so an inaudible utterance while Jarvis thinks keeps it warm.
     */
    fun warmUp() {
        if (ready) tts.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "warmup")
    }

    /** Switches to the voice called [name], remembers it, and says a sample. */
    fun use(name: String) {
        if (name == "list") return logVoices()
        val voice = tts.voices?.firstOrNull { it.name == name } ?: run {
            Log.w(TAG, "no voice $name")
            return
        }
        tts.voice = voice
        prefs.edit().putString(KEY_VOICE, name).apply()
        say("Hi, I'm Jarvis. Tonight I'd suggest Gullak, on Sony LIV.")
    }

    private fun logVoices() {
        Log.i(TAG, "current: ${tts.voice?.name}")
        tts.voices.orEmpty().filter { it.locale.language == "en" }.sortedBy { it.name }.forEach {
            Log.i(TAG, "available: ${it.name} quality=${it.quality} network=${it.isNetworkConnectionRequired}")
        }
    }

    fun stop() {
        if (ready) tts.stop()
    }

    private companion object {
        const val TAG = "JarvisVoice"
        const val KEY_VOICE = "voice"
        const val DEFAULT_VOICE = "en-in-x-end-network"
    }
}

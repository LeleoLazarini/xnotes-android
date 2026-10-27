package com.xnotes.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous speech-to-text in Brazilian Portuguese for meetings. Android's recogniser stops after
 * each utterance, so this starts it again whenever it ends, until [stop]. It prefers the on-device
 * recogniser (no internet, nothing leaves the tablet) and falls back to the default one. Must be
 * used from the main thread, as [SpeechRecognizer] requires.
 */
class LiveTranscriber(
    private val context: Context,
    /** The words recognised so far plus the phrase still being heard. */
    private val onText: (committed: String, partial: String) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private val committed = StringBuilder()
    private var errorsInRow = 0

    fun start() {
        if (running) return
        running = true
        listen()
    }

    fun stop(): String {
        running = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        return committed.toString().trim()
    }

    private fun create(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun listen() {
        if (!running) return
        val r = recognizer ?: create().also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onText(committed.toString().trim(), t)
            }

            override fun onResults(results: Bundle?) {
                errorsInRow = 0
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (t.isNotEmpty()) committed.append(t.replaceFirstChar { it.uppercase() }).append(". ")
                onText(committed.toString().trim(), "")
                listen() // keep going: the recogniser ends after every utterance
            }

            override fun onError(error: Int) {
                if (!running) return
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { running = false; onProblem("permission") }
                    // Silence or nothing understood: just listen again.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                    else -> {
                        errorsInRow++
                        if (errorsInRow >= MAX_ERRORS_IN_ROW) { running = false; onProblem("error:$error") }
                        else { runCatching { recognizer?.destroy() }; recognizer = null; listen() }
                    }
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Pauses between speakers shouldn't end a phrase too eagerly.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        runCatching { r.startListening(intent) }.onFailure { onProblem("start") ; running = false }
    }

    private companion object {
        const val MAX_ERRORS_IN_ROW = 5
    }
}

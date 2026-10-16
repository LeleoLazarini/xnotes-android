package com.xnotes.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A one-off diagnostic for meeting mode: what speech recognition this device offers in pt-BR,
 * and whether the app can record the audio itself while the recogniser transcribes it. The
 * report is meant to be copied and sent back, so its wording is plain and fixed (Portuguese).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranscriptionTestDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val report = remember { mutableStateListOf<String>() }
    var live by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasMic(context)) }
    val session = remember { TestSession() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        report.add(if (ok) "Microfone: permitido" else "Microfone: NEGADO")
    }

    LaunchedEffect(Unit) {
        report.add("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        report.add("Reconhecedor (qualquer): ${SpeechRecognizer.isRecognitionAvailable(context)}")
        if (Build.VERSION.SDK_INT >= 31) {
            report.add("Reconhecedor no aparelho (offline): ${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
        }
        if (Build.VERSION.SDK_INT >= 33) checkSupport(context) { report.add(it) }
    }
    DisposableEffect(Unit) { onDispose { session.stop() } }

    fun run(fromApp: Boolean) {
        if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        live = ""
        running = true
        report.add(if (fromApp) "— Teste B: o app grava e entrega o áudio ao reconhecedor —" else "— Teste A: reconhecedor ouve o microfone direto —")
        session.start(context, fromApp, onLive = { live = it }, onLog = { report.add(it) })
        session.timer = scope.launch {
            for (s in TEST_SECONDS downTo 1) { if (!running) return@launch; delay(1000) }
            session.finish { report.add(it) }
            delay(3000)
            session.stop()
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = { session.stop(); onDismiss() },
        title = { Text("Teste de transcrição") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Fale normalmente perto do tablet durante os ${TEST_SECONDS} s de cada teste. Depois toque em Copiar relatório e me mande.",
                    fontSize = 13.sp,
                    color = palette.textDim.toComposeColor(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!granted) OutlinedButton(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Permitir microfone") }
                    if (Build.VERSION.SDK_INT >= 33) OutlinedButton(enabled = !running, onClick = { downloadPortuguese(context) { report.add(it) } }) { Text("Baixar português") }
                    OutlinedButton(enabled = !running, onClick = { run(fromApp = false) }) { Text("Teste A") }
                    if (Build.VERSION.SDK_INT >= 33) OutlinedButton(enabled = !running, onClick = { run(fromApp = true) }) { Text("Teste B") }
                    OutlinedButton(onClick = { copy(context, report.joinToString("\n")) }) { Text("Copiar relatório") }
                }
                if (live.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(live, fontSize = 15.sp, color = palette.text.toComposeColor())
                }
                Spacer(Modifier.height(10.dp))
                report.forEach { Text(it, fontSize = 12.sp, color = palette.textDim.toComposeColor()) }
            }
        },
        confirmButton = { TextButton(onClick = { session.stop(); onDismiss() }) { Text("Fechar") } },
    )
}

private const val TEST_SECONDS = 20
private const val RATE = 16000
private const val LANG = "pt-BR"

private fun hasMic(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Relatório de transcrição", text))
}

private fun baseIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

/** The offline recogniser when the device has one, else the default (usually Google, online). */
private fun newRecognizer(context: Context, log: (String) -> Unit): SpeechRecognizer =
    if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        log("Usando: reconhecedor no aparelho")
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        log("Usando: reconhecedor padrão")
        SpeechRecognizer.createSpeechRecognizer(context)
    }

@androidx.annotation.RequiresApi(33)
private fun checkSupport(context: Context, log: (String) -> Unit) {
    val r = newRecognizer(context) {}
    r.checkRecognitionSupport(baseIntent(), Executors.newSingleThreadExecutor(), object : RecognitionSupportCallback {
        override fun onSupportResult(s: RecognitionSupport) {
            fun pt(list: List<String>) = list.filter { it.startsWith("pt", ignoreCase = true) }.ifEmpty { listOf("nenhum pt") }.joinToString()
            log("pt instalado offline: ${pt(s.installedOnDeviceLanguages)}")
            log("pt baixando: ${pt(s.pendingOnDeviceLanguages)}")
            log("pt disponível p/ baixar: ${pt(s.supportedOnDeviceLanguages)}")
            log("pt online: ${pt(s.onlineLanguages)}")
            ContextCompat.getMainExecutor(context).execute { r.destroy() }
        }

        override fun onError(error: Int) {
            log("Consulta de idiomas falhou: ${errorName(error)}")
            ContextCompat.getMainExecutor(context).execute { r.destroy() }
        }
    })
}

@androidx.annotation.RequiresApi(33)
private fun downloadPortuguese(context: Context, log: (String) -> Unit) {
    val r = newRecognizer(context) {}
    runCatching { r.triggerModelDownload(baseIntent()) }
        .onSuccess { log("Pedido de download do português enviado; aguarde e rode o Teste A de novo.") }
        .onFailure { log("Download não suportado: ${it.message}") }
    r.destroy()
}

private fun errorName(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "sem rede (timeout)"
    SpeechRecognizer.ERROR_NETWORK -> "sem rede"
    SpeechRecognizer.ERROR_AUDIO -> "erro de áudio"
    SpeechRecognizer.ERROR_SERVER -> "erro do servidor"
    SpeechRecognizer.ERROR_CLIENT -> "erro do cliente"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ninguém falou"
    SpeechRecognizer.ERROR_NO_MATCH -> "não entendeu"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "reconhecedor ocupado"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "sem permissão"
    12 -> "idioma não suportado"
    13 -> "idioma não instalado"
    else -> "código $code"
} + " ($code)"

/** One test run: the recogniser, and in test B the app's own recording feeding it. */
private class TestSession {
    var timer: Job? = null
    private var recognizer: SpeechRecognizer? = null
    private var record: AudioRecord? = null
    private var pipe: Array<ParcelFileDescriptor>? = null
    @Volatile private var pumping = false
    @Volatile private var bytes = 0L
    private val finals = StringBuilder()
    private var segments = 0
    private var started = 0L
    private var firstText = 0L

    fun start(context: Context, fromApp: Boolean, onLive: (String) -> Unit, onLog: (String) -> Unit) {
        stop()
        finals.clear(); segments = 0; bytes = 0; firstText = 0
        started = System.currentTimeMillis()
        val r = newRecognizer(context, onLog)
        recognizer = r
        val intent = baseIntent()
        if (fromApp && Build.VERSION.SDK_INT >= 33) {
            val p = ParcelFileDescriptor.createPipe()
            pipe = p
            if (!startRecording(p[1], onLog)) return
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, p[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        } else if (Build.VERSION.SDK_INT >= 33) {
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onLog("Pronto para ouvir")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) = onLog("Erro: ${errorName(error)}")

            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank() && firstText == 0L) firstText = System.currentTimeMillis()
                onLive((finals.toString() + " " + t).trim())
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) finals.append(t).append(' ')
                onLive(finals.toString().trim())
                onLog("Resultado final recebido")
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                val t = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) { finals.append(t).append(' '); segments++ }
                onLive(finals.toString().trim())
            }

            override fun onEndOfSegmentedSession() = onLog("Sessão contínua encerrada")
        })
        r.startListening(intent)
    }

    private fun startRecording(sink: ParcelFileDescriptor, onLog: (String) -> Unit): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            @Suppress("MissingPermission")
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            onLog("Gravação do app: NÃO iniciou")
            return false
        }
        record = rec
        rec.startRecording()
        pumping = true
        Thread {
            val out = ParcelFileDescriptor.AutoCloseOutputStream(sink)
            val buf = ByteArray(min)
            runCatching {
                while (pumping) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) { out.write(buf, 0, n); bytes += n }
                }
            }
            runCatching { out.close() }
        }.start()
        onLog("Gravação do app: iniciada")
        return true
    }

    /** Ask the recogniser to wrap up and log what the run produced. */
    fun finish(onLog: (String) -> Unit) {
        pumping = false
        recognizer?.stopListening()
        val secs = bytes / (RATE * 2.0)
        if (record != null) onLog("Áudio gravado pelo app: %.1f s".format(secs))
        if (firstText > 0) onLog("Primeiro texto após %.1f s".format((firstText - started) / 1000.0))
        onLog("Trechos contínuos: $segments")
        onLog("Texto: " + finals.toString().trim().ifEmpty { "(vazio)" })
    }

    fun stop() {
        timer?.cancel(); timer = null
        pumping = false
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { recognizer?.destroy() }; recognizer = null
        pipe?.forEach { runCatching { it.close() } }; pipe = null
    }
}

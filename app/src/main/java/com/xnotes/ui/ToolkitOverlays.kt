package com.xnotes.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.xnotes.R
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The toolkit's floating pieces over the canvas: the handwriting and picture-text results, the
 * link picker, the recording and playback bars, and the scanner and microphone launches.
 */
@Composable
internal fun ToolkitOverlays(editor: Editor) {
    editor.inkConversion?.let { InkConversionDialog(editor, it) }
    editor.imageTextResult?.let { ImageTextDialog(editor, it) }
    if (editor.linkPicking) LinkPickerDialog(editor)
    editor.translation?.let { TranslationDialog(editor, it) }
    editor.pageModelSaveFor?.let { SavePageModelDialog(editor, it) }
    editor.pageModelPickAfter?.let { PageModelPicker(editor, it) }
    editor.reminder?.let { ReminderDialog(editor, it) }
    ScreenRecordLauncher(editor)
    AgendaCalendarSync(editor)
    if (editor.layersOpen) LayersDialog(editor)
    if (editor.focusMode) FocusControls(editor)
    editor.study?.let { StudyControls(editor, it) }
    RecordingControls(editor)
    TranscriptionControls(editor)
    editor.transcriptResult?.let { TranscriptDialog(editor, it) }
    if (editor.transcribing) TranscriptPanel(editor)
    ScannerLauncher(editor)
    Box(Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            editor.recording?.let {
                var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
                LaunchedEffect(it) { while (true) { now = android.os.SystemClock.uptimeMillis(); delay(500) } }
                Pill(Color(0xFFE53935), stringResource(R.string.audio_recording, clock(now - editor.recordingStartedAt)), stringResource(R.string.audio_stop)) { editor.stopRecording() }
            }
            editor.playing?.let { Pill(Color(0xFF2563EB), stringResource(R.string.audio_playing), stringResource(R.string.audio_stop)) { editor.stopAudio() } }
            if (com.xnotes.platform.ScreenRecorder.running) {
                val ctx = LocalContext.current
                var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = android.os.SystemClock.uptimeMillis(); delay(500) } }
                Pill(Color(0xFFE53935), stringResource(R.string.screen_rec_pill, clock(now - com.xnotes.platform.ScreenRecorder.startedAt)), stringResource(R.string.audio_stop)) { com.xnotes.platform.ScreenRecorder.stop(ctx) }
            }
        }
    }
    if (editor.veiled) LockVeil(editor)
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** A floating status pill: a coloured dot, what is happening, and one action. */
@Composable
private fun Pill(dot: Color, label: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xE6202124)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.size(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.size(10.dp))
        Text(
            action,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Asks for the microphone when a transcription is requested, then starts it. */
@Composable
private fun TranscriptionControls(editor: Editor) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        editor.transcribeRequested = false
        if (ok) editor.startTranscription() else editor.message = context.getString(R.string.audio_permission)
    }
    LaunchedEffect(editor.transcribeRequested) {
        if (!editor.transcribeRequested) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            editor.transcribeRequested = false
            editor.startTranscription()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** The live transcript at the bottom of the page: the last lines said, and Stop. */
@Composable
private fun TranscriptPanel(editor: Editor) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xEE202124)).padding(14.dp).heightIn(max = 200.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.meeting_listening), color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.audio_stop),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF)).clickable { editor.stopTranscription() }.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            val shown = (editor.transcriptSoFar + " " + editor.transcriptPartial).trim().takeLast(420)
            Text(shown.ifEmpty { "…" }, color = Color(0xFFE5E7EB), fontSize = 15.sp)
        }
    }
}

@Composable
private fun TranscriptDialog(editor: Editor, text0: String) {
    var text by remember(text0) { mutableStateOf(text0) }
    AlertDialog(
        onDismissRequest = { editor.transcriptResult = null },
        title = { Text(stringResource(R.string.meeting_done_title)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4) } },
        confirmButton = {
            Row {
                TextButton(onClick = { editor.placeTranscript(text, newPage = true) }) { Text(stringResource(R.string.meeting_new_page)) }
                TextButton(onClick = { editor.placeTranscript(text, newPage = false) }) { Text(stringResource(R.string.meeting_this_page)) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { editor.summarizeWithClaude(text) }) { Text(stringResource(R.string.summary_with_claude)) }
                CopyButton(text) { editor.transcriptResult = null }
            }
        },
    )
}

/** Asks for the microphone when a recording is requested, then starts it. */
@Composable
private fun RecordingControls(editor: Editor) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        editor.recordRequested = false
        if (ok) editor.startRecording() else editor.message = context.getString(R.string.audio_permission)
    }
    LaunchedEffect(editor.recordRequested) {
        if (!editor.recordRequested) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            editor.recordRequested = false
            editor.startRecording()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Opens the system document scanner and places what it returns, one page per scan. */
@Composable
private fun ScannerLauncher(editor: Editor) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data) ?: return@rememberLauncherForActivityResult
        val uris = result.pages?.map { it.imageUri }.orEmpty()
        scope.launch {
            val images = withContext(Dispatchers.IO) { uris.mapNotNull { u -> runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() } }
            editor.insertScannedPages(images)
        }
    }
    LaunchedEffect(editor.scanRequested) {
        if (!editor.scanRequested) return@LaunchedEffect
        editor.scanRequested = false
        val activity = context as? Activity ?: return@LaunchedEffect
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { editor.message = context.getString(R.string.scan_unavailable) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InkConversionDialog(editor: Editor, c: Editor.InkConversion) {
    val palette = LocalPalette.current
    var text by remember(c) { mutableStateOf(c.text) }
    var keep by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { editor.dismissInkConversion() },
        title = { Text(stringResource(R.string.convert_to_text)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2)
                if (c.alternatives.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.ink_alternatives), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                    Spacer(Modifier.size(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.alternatives.forEach { alt -> ModeChip(alt, alt == text) { text = alt } }
                    }
                }
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text(stringResource(R.string.ink_keep), color = palette.text.toComposeColor(), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { editor.applyInkConversion(text, keep) }) { Text(stringResource(R.string.ink_place)) } },
        dismissButton = {
            Row {
                CopyButton(text) { editor.dismissInkConversion() }
                TextButton(onClick = { editor.dismissInkConversion() }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ImageTextDialog(editor: Editor, r: Editor.ImageTextResult) {
    var text by remember(r) { mutableStateOf(r.text) }
    AlertDialog(
        onDismissRequest = { editor.dismissImageText() },
        title = { Text(stringResource(R.string.read_image_text)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3) } },
        confirmButton = { TextButton(onClick = { editor.placeImageText(text) }) { Text(stringResource(R.string.ink_place)) } },
        dismissButton = {
            Row {
                CopyButton(text) { editor.dismissImageText() }
                TextButton(onClick = { editor.dismissImageText() }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationDialog(editor: Editor, job: Editor.TranslationJob) {
    val palette = LocalPalette.current
    var result by remember(job.result) { mutableStateOf(job.result.orEmpty()) }
    AlertDialog(
        onDismissRequest = { editor.translation = null },
        title = { Text(stringResource(R.string.translate)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(job.source, color = palette.textDim.toComposeColor(), fontSize = 13.sp)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.translate_to), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                Spacer(Modifier.size(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.xnotes.platform.Translate.targets.forEach { (code, label) -> ModeChip(label, false) { editor.runTranslation(code) } }
                }
                Spacer(Modifier.size(10.dp))
                when {
                    job.busy -> Text(stringResource(R.string.translate_busy), color = palette.textDim.toComposeColor(), fontSize = 13.sp)
                    job.result != null -> OutlinedTextField(value = result, onValueChange = { result = it }, minLines = 2)
                }
            }
        },
        confirmButton = { TextButton(onClick = { editor.placeTranslation(result) }, enabled = job.result != null && !job.busy) { Text(stringResource(R.string.ink_place)) } },
        dismissButton = { CopyButton(result) { editor.translation = null } },
    )
}

@Composable
private fun CopyButton(text: String, after: () -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("xnotes", text))
        after()
    }) { Text(stringResource(R.string.copy)) }
}

/** Choose the page the selection links to: a bookmark, or a page number. */
@Composable
private fun LinkPickerDialog(editor: Editor) {
    val palette = LocalPalette.current
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { editor.linkPicking = false },
        title = { Text(stringResource(R.string.link_to_page)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = number, onValueChange = { v -> number = v.filter { it.isDigit() }.take(5) }, singleLine = true, label = { Text(stringResource(R.string.link_page_number, editor.pageCount)) })
                if (editor.bookmarks.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.link_or_bookmark), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                    editor.bookmarks.sortedBy { it.page }.forEach { b ->
                        Text(
                            "${b.label}  ·  ${b.page + 1}",
                            color = palette.text.toComposeColor(),
                            fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editor.applyLink(b.page) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { number.toIntOrNull()?.let { n -> editor.applyLink((n - 1).coerceIn(0, editor.pageCount - 1)) } }, enabled = number.toIntOrNull() != null) { Text(stringResource(R.string.link_apply)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { editor.applyLink(null) }) { Text(stringResource(R.string.link_clear)) }
                TextButton(onClick = { editor.linkPicking = false }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun SavePageModelDialog(editor: Editor, index: Int) {
    var name by remember(index) { mutableStateOf("") }
    val close = { editor.pageModelSaveFor = null }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.page_model_save)) },
        text = {
            Column {
                Text(stringResource(R.string.page_model_save_help), fontSize = 13.sp, color = LocalPalette.current.textDim.toComposeColor())
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.page_model_name_hint)) })
            }
        },
        confirmButton = { TextButton(onClick = { editor.savePageModel(index, name.trim()); close() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PageModelPicker(editor: Editor, after: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalPalette.current
    var models by remember { mutableStateOf(com.xnotes.platform.PageModels.list(context)) }
    val close = { editor.pageModelPickAfter = null }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.page_model_insert)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (models.isEmpty()) Text(stringResource(R.string.page_model_none), fontSize = 14.sp, color = palette.textDim.toComposeColor())
                models.forEach { m ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            m,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f).clickable { close(); editor.insertPageModel(m, after) }.padding(vertical = 12.dp),
                        )
                        TextButton(onClick = { com.xnotes.platform.PageModels.delete(context, m); models = com.xnotes.platform.PageModels.list(context) }) {
                            Text(stringResource(R.string.delete), color = palette.textDim.toComposeColor())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A dark rounded button for the floating bars. */
@Composable
private fun BarButton(label: String, strong: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (strong) Color(0xFF2563EB) else Color(0x33FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun DarkBar(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xD9202124)).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Focus and presentation: the way out, and in presentation the page arrows. */
@Composable
private fun FocusControls(editor: Editor) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopEnd) {
        DarkBar {
            if (editor.presenting) {
                BarButton("‹") { editor.goToPage((editor.pageIndex - 1).coerceAtLeast(0)) }
                Text("${editor.pageIndex + 1} / ${editor.pageCount}", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                BarButton("›") { editor.goToPage((editor.pageIndex + 1).coerceAtMost(editor.pageCount - 1)) }
            }
            BarButton(stringResource(R.string.focus_exit)) { editor.exitFocus() }
        }
    }
}

/** The study run: which card, show the answer, score it; at the end the score and a retry. */
@Composable
private fun StudyControls(editor: Editor, s: Editor.StudySession) {
    Box(Modifier.fillMaxSize().padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        DarkBar {
            if (s.done) {
                Text(stringResource(R.string.study_done, s.right, s.cards.size), color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                if (s.missed.isNotEmpty()) BarButton(stringResource(R.string.study_retry, s.missed.size), strong = true) { editor.startStudy(s.missed.toList()) }
                BarButton(stringResource(R.string.study_close)) { editor.endStudy() }
            } else {
                Text(stringResource(R.string.study_card, s.at + 1, s.cards.size), color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                if (!s.shown) BarButton(stringResource(R.string.study_show), strong = true) { editor.studyReveal() }
                else {
                    BarButton(stringResource(R.string.study_wrong)) { editor.studyAnswer(false) }
                    BarButton(stringResource(R.string.study_right), strong = true) { editor.studyAnswer(true) }
                }
                BarButton(stringResource(R.string.study_close)) { editor.endStudy() }
            }
        }
    }
}

@Composable
private fun ReminderDialog(editor: Editor, r: Editor.ReminderDraft) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var title by remember(r) { mutableStateOf(r.title) }
    var at by remember(r) { mutableStateOf(r.at.timeInMillis) }
    fun cal() = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val shown = java.text.SimpleDateFormat("EEE, dd/MM 'às' HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(at))
    AlertDialog(
        onDismissRequest = { editor.reminder = null },
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, minLines = 1)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.reminder_when, shown), fontSize = 15.sp)
                Spacer(Modifier.size(6.dp))
                Row {
                    TextButton(onClick = {
                        val c = cal()
                        android.app.DatePickerDialog(context, { _, y, m, d -> at = cal().apply { set(y, m, d) }.timeInMillis },
                            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
                    }) { Text(stringResource(R.string.reminder_date)) }
                    TextButton(onClick = {
                        val c = cal()
                        android.app.TimePickerDialog(context, { _, h, min -> at = cal().apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, min) }.timeInMillis },
                            c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show()
                    }) { Text(stringResource(R.string.reminder_time)) }
                }
                Text(stringResource(R.string.reminder_help), fontSize = 12.sp, color = palette.textDim.toComposeColor())
            }
        },
        confirmButton = { TextButton(onClick = { editor.createReminder(title.trim(), cal()) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.reminder_create)) } },
        dismissButton = { TextButton(onClick = { editor.reminder = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Over a locked note: nothing of the page, one button to open it with the fingerprint. */
@Composable
private fun LockVeil(editor: Editor) {
    val activity = LocalContext.current as? Activity
    val palette = LocalPalette.current
    fun open() { activity?.let { LockGate.authenticate(it, { editor.unlockCurrent() }) } }
    LaunchedEffect(editor.title) { open() } // asks at once on opening; the button is there if it is dismissed
    Box(
        Modifier.fillMaxSize().background(palette.surface.toComposeColor())
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.lock_veil_text), fontSize = 16.sp, color = palette.text.toComposeColor())
            Spacer(Modifier.size(16.dp))
            BarButton(stringResource(R.string.lock_open), strong = true) { open() }
        }
    }
}

/** Asks for the microphone (optional) and the screen, then starts the recording service. */
@Composable
private fun ScreenRecordLauncher(editor: Editor) {
    val context = LocalContext.current
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val i = android.content.Intent(context, com.xnotes.platform.ScreenRecorder::class.java)
            .putExtra(com.xnotes.platform.ScreenRecorder.EXTRA_CODE, res.resultCode)
            .putExtra(com.xnotes.platform.ScreenRecorder.EXTRA_DATA, data)
        ContextCompat.startForegroundService(context, i)
    }
    fun askScreen() {
        val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
        capture.launch(mpm.createScreenCaptureIntent())
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { askScreen() }
    LaunchedEffect(editor.screenRecordRequested) {
        if (!editor.screenRecordRequested) return@LaunchedEffect
        editor.screenRecordRequested = false
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) askScreen() else perms.launch(wanted.toTypedArray())
    }
    val finished = com.xnotes.platform.ScreenRecorder.finishedCount
    LaunchedEffect(finished) {
        if (finished == 0) return@LaunchedEffect
        editor.message = context.getString(if (com.xnotes.platform.ScreenRecorder.lastSaved) R.string.screen_rec_saved else R.string.screen_rec_failed)
    }
}

/** An agenda fetches the calendar quietly when it opens (once allowed), and on request from the menu. */
@Composable
private fun AgendaCalendarSync(editor: Editor) {
    val context = LocalContext.current
    val granted = { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) editor.syncAgendaCalendar(quiet = false) else editor.message = context.getString(R.string.agenda_cal_permission)
    }
    LaunchedEffect(editor.title) { if (editor.isAgenda && granted()) editor.syncAgendaCalendar(quiet = true) }
    LaunchedEffect(editor.calendarRequested) {
        if (!editor.calendarRequested) return@LaunchedEffect
        editor.calendarRequested = false
        if (granted()) editor.syncAgendaCalendar(quiet = false) else permission.launch(Manifest.permission.READ_CALENDAR)
    }
}

/** The note's layers, top first: which one you draw on, and each one's eye, lock, name and removal. */
@Composable
private fun LayersDialog(editor: Editor) {
    val palette = LocalPalette.current
    var renaming by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    val layers = editor.layers
    AlertDialog(
        onDismissRequest = { editor.layersOpen = false },
        title = { Text(stringResource(R.string.layers_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                for (i in layers.indices.reversed()) {
                    val l = layers[i]
                    val active = i == editor.activeLayer
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (active) palette.accent.withAlpha(40).toComposeColor() else Color.Transparent)
                            .clickable { editor.setActiveLayer(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (active) "●" else "○", fontSize = 16.sp, color = palette.accent.toComposeColor())
                        Spacer(Modifier.size(8.dp))
                        if (renaming == i) {
                            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editor.renameLayer(i, name.trim()); renaming = null }) { Text(stringResource(R.string.save)) }
                        } else {
                            Text(l.name, fontSize = 15.sp, color = if (l.hidden) palette.textDim.toComposeColor() else palette.text.toComposeColor(),
                                modifier = Modifier.weight(1f).clickable { renaming = i; name = l.name })
                            TextButton(onClick = { editor.toggleLayerHidden(i) }) { Text(stringResource(if (l.hidden) R.string.layer_show else R.string.layer_hide)) }
                            TextButton(onClick = { editor.toggleLayerLocked(i) }) { Text(stringResource(if (l.locked) R.string.layer_unlock else R.string.layer_lock)) }
                            if (layers.size > 1) TextButton(onClick = { editor.mergeLayerDown(i) }) { Text(stringResource(R.string.layer_remove), color = palette.textDim.toComposeColor()) }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.layers_help), fontSize = 12.sp, color = palette.textDim.toComposeColor())
            }
        },
        confirmButton = { TextButton(onClick = { editor.addLayer() }) { Text(stringResource(R.string.layer_add)) } },
        dismissButton = { TextButton(onClick = { editor.layersOpen = false }) { Text(stringResource(R.string.study_close)) } },
    )
}

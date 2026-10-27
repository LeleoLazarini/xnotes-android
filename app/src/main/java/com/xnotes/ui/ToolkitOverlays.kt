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
        }
    }
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
        dismissButton = { CopyButton(text) { editor.transcriptResult = null } },
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

package com.xnotes.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.platform.Backup
import com.xnotes.settings.Preferences
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Copy the notes folder into the backup folder now; the message says how it went. */
internal suspend fun runBackup(editor: Editor, resolver: android.content.ContentResolver): Boolean {
    val prefs = editor.preferences
    val dest = prefs.backupFolder ?: return false
    val root = editor.browseRoot ?: return false
    val n = withContext(Dispatchers.IO) { Backup.run(resolver, root, dest) }
    if (n == null) {
        editor.message = editor.appString(R.string.backup_failed)
        return false
    }
    editor.applyHomePreferences(editor.preferences.copy(lastBackupAt = System.currentTimeMillis()))
    return true
}

/** The daily backup: a few seconds after launch, when a folder is set and a day has passed. */
@Composable
internal fun DailyBackup(editor: Editor) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        val p = editor.preferences
        if (p.backupFolder != null && System.currentTimeMillis() - p.lastBackupAt > DAY_MS) runBackup(editor, context.contentResolver)
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Preferences: where the backup goes, when it last ran, and a button to run it now. */
@Composable
internal fun BackupSection(editor: Editor, prefs: Preferences, update: (Preferences) -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        update(prefs.copy(backupFolder = uri.toString()))
    }
    FieldLabel(stringResource(R.string.pref_backup))
    val where = prefs.backupFolder?.let { android.net.Uri.parse(it).lastPathSegment?.substringAfterLast(':') } ?: stringResource(R.string.backup_off)
    val last = if (prefs.lastBackupAt == 0L) stringResource(R.string.backup_never)
    else java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(prefs.lastBackupAt))
    Text(stringResource(R.string.backup_status, where, last), color = palette.textDim.toComposeColor(), fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.backup_choose), false) { pick.launch(null) }
        if (prefs.backupFolder != null) {
            Chip(stringResource(if (running) R.string.backup_running else R.string.backup_now), running) {
                if (running) return@Chip
                running = true
                scope.launch {
                    if (runBackup(editor, context.contentResolver)) editor.message = context.getString(R.string.backup_done)
                    running = false
                }
            }
            Chip(stringResource(R.string.backup_turn_off), false) { update(prefs.copy(backupFolder = null)) }
        }
    }
}

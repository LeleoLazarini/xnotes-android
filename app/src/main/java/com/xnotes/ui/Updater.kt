package com.xnotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.xnotes.R
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the GitHub releases of the personal fork: a release tagged `v<versionCode>` with the
 * APK attached is newer when its number beats the installed one. The check runs quietly a few
 * seconds after launch (at most every few hours) or on demand; installing hands the download to
 * the system installer, which asks once for permission to install from this app.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/LeleoLazarini/xnotes-android/releases/latest"
    private const val PREFS = "updater"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    class Release(val code: Long, val name: String, val notes: String, val apkUrl: String)

    /** A newer release waiting to be offered, or null. */
    var available by mutableStateOf<Release?>(null)

    /** Download progress 0..1 while fetching, else null. */
    var progress by mutableStateOf<Float?>(null)

    /** A one-line status for a check the user asked for ("up to date", "no connection"). */
    var status by mutableStateOf<String?>(null)

    private fun installedCode(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)

    /** Look for a newer release; [manual] also reports "up to date" and failures. */
    suspend fun check(context: Context, manual: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("last", 0L) < CHECK_EVERY_MS) return
        prefs.edit().putLong("last", now).apply()
        val release = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() }
        when {
            release == null -> if (manual) status = context.getString(R.string.update_failed)
            release.code > installedCode(context) -> available = release
            manual -> status = context.getString(R.string.update_none)
        }
    }

    private fun fetchLatest(): Release? {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val o = org.json.JSONObject(body)
        val code = o.optString("tag_name").removePrefix("v").toLongOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return Release(code, o.optString("name"), o.optString("body"), apk.optString("browser_download_url"))
    }

    /** Fetch [r]'s APK and open the system installer on it. */
    suspend fun install(context: Context, r: Release) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            status = context.getString(R.string.update_allow_install)
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        progress = 0f
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "xnotes-update.apk")
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                val total = c.contentLengthLong.coerceAtLeast(1L)
                var read = 0L
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            read += n
                            progress = (read.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
                out
            }.getOrNull()
        }
        progress = null
        if (file == null) { status = context.getString(R.string.update_failed); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        available = null
    }
}

/** The quiet check after launch, and the dialog that offers a newer build. */
@Composable
internal fun UpdateOverlay(editor: Editor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4_000)
        Updater.check(context, manual = false)
    }
    Updater.status?.let { s ->
        LaunchedEffect(s) {
            editor.message = s
            Updater.status = null
        }
    }
    val r = Updater.available ?: return
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = { if (Updater.progress == null) Updater.available = null },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(r.name.ifBlank { "v${r.code}" }, color = palette.text.toComposeColor(), fontSize = 15.sp)
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(r.notes, color = palette.textDim.toComposeColor(), fontSize = 13.sp)
                }
                Updater.progress?.let {
                    Spacer(Modifier.size(12.dp))
                    LinearProgressIndicator(progress = { it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { Updater.install(context, r) } }, enabled = Updater.progress == null) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = { TextButton(onClick = { Updater.available = null }, enabled = Updater.progress == null) { Text(stringResource(R.string.update_later)) } },
    )
}

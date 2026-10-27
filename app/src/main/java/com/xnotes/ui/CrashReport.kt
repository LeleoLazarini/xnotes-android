package com.xnotes.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * A black box for crashes on a tablet with no cable to a computer: the stack trace of a crash is
 * kept, and the next launch shows it (with a share button) instead of crashing again unseen.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(0L)
                file(app).writeText("xnotes v$version · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n\n" + android.util.Log.getStackTraceString(error))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) { file(context).delete() }
}

@Composable
fun CrashScreen(activity: Activity, report: String) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F4EE)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("O xnotes fechou sozinho da última vez", fontSize = 22.sp, color = Color(0xFF1F2937))
        Text("Toque em Compartilhar erro e mande o texto para o Claude. Depois, Abrir o app tenta de novo.", fontSize = 15.sp, color = Color(0xFF4B5563))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Compartilhar erro"))
            }) { Text("Compartilhar erro") }
            OutlinedButton(onClick = { CrashReport.clear(activity); activity.recreate() }) { Text("Abrir o app") }
        }
        Text(
            report,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF111827),
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White).padding(12.dp).verticalScroll(rememberScrollState()),
        )
    }
}

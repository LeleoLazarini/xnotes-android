package com.xnotes.platform

import android.content.Context
import java.io.File

/**
 * The user's own page models: a page saved once (a meeting form, a checklist) and inserted again
 * whenever needed. Each model is a one-page .xnote in the app's private storage, so it keeps
 * everything a page can hold - ink, text, shapes, images, its paper and colour.
 */
object PageModels {
    private fun dir(context: Context) = File(context.filesDir, "page-models").apply { mkdirs() }

    fun list(context: Context): List<String> =
        dir(context).listFiles { f -> f.name.endsWith(EXT) }.orEmpty().map { it.name.removeSuffix(EXT) }.sortedBy { it.lowercase() }

    fun file(context: Context, name: String) = File(dir(context), safe(name) + EXT)

    fun delete(context: Context, name: String) { file(context, name).delete() }

    private fun safe(name: String) = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "modelo" }

    private const val EXT = ".xnote"
}

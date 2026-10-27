package com.xnotes.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies the whole notes folder (sub-folders, notes, canvases, and the hidden .xnote sidecars that
 * hold colours and folder icons) into a dated folder inside a destination the user picked, then
 * keeps only the newest [KEEP] copies there. Everything goes through the Storage Access Framework,
 * so the destination can be any folder a document provider offers: the tablet, an SD card, or a
 * cloud app that supports folders.
 */
object Backup {
    const val KEEP = 10
    private const val PREFIX = "xnotes-"

    /** Back [sourceTree] up into [destTree]; the number of files copied, or null when it failed. */
    fun run(resolver: ContentResolver, sourceTree: String, destTree: String): Int? = runCatching {
        val src = Uri.parse(sourceTree)
        val dst = Uri.parse(destTree)
        val dstRoot = DocumentsContract.buildDocumentUriUsingTree(dst, DocumentsContract.getTreeDocumentId(dst))
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val target = DocumentsContract.createDocument(resolver, dstRoot, DocumentsContract.Document.MIME_TYPE_DIR, PREFIX + stamp) ?: return null
        val count = copyChildren(resolver, src, DocumentsContract.getTreeDocumentId(src), target)
        prune(resolver, dst)
        count
    }.getOrNull()

    private class Child(val id: String, val name: String, val isDir: Boolean)

    private fun children(resolver: ContentResolver, tree: Uri, docId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = ArrayList<Child>()
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out += Child(id, name, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return out
    }

    private fun copyChildren(resolver: ContentResolver, tree: Uri, docId: String, into: Uri): Int {
        var n = 0
        for (child in children(resolver, tree, docId)) {
            if (child.isDir) {
                val dir = DocumentsContract.createDocument(resolver, into, DocumentsContract.Document.MIME_TYPE_DIR, child.name) ?: continue
                n += copyChildren(resolver, tree, child.id, dir)
            } else {
                val out = DocumentsContract.createDocument(resolver, into, "application/octet-stream", child.name) ?: continue
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, child.id))?.use { input ->
                    resolver.openOutputStream(out, "w")?.use { input.copyTo(it) }
                }
                n++
            }
        }
        return n
    }

    /** Delete all but the newest [KEEP] dated copies in [destTree]. */
    private fun prune(resolver: ContentResolver, destTree: Uri) {
        val rootId = DocumentsContract.getTreeDocumentId(destTree)
        val old = children(resolver, destTree, rootId).filter { it.isDir && it.name.startsWith(PREFIX) }.sortedByDescending { it.name }.drop(KEEP)
        for (c in old) runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(destTree, c.id)) }
    }
}

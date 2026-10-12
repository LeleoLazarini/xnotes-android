package com.xnotes.core.util

/**
 * Where each document of a batch PDF export lands in its zip. A picked folder keeps its whole tree
 * under its own name, empty folders included; a picked document sits at the top; every document
 * becomes `<name>.pdf`. Names that would clash in one folder are numbered, ignoring case as most
 * file systems do, and a pick that already sits inside another picked folder lands only there.
 */
object ExportZip {

    /**
     * A folder or document as the explorer lists it. [key] identifies it however it was reached,
     * [source] is what gets exported, and a folder holds its [children].
     */
    class Node(
        val name: String,
        val key: String,
        val source: String,
        val isDir: Boolean,
        val children: List<Node> = emptyList(),
    )

    /** One entry of the zip: a folder when [source] is null, its [path] ending in '/', else the PDF of [source]. */
    data class Entry(val path: String, val source: String?)

    /** The zip's entries for [picked], each folder before what it holds. */
    fun plan(picked: List<Node>): List<Entry> {
        val inside = HashSet<String>()
        for (n in picked) if (n.isDir) collectKeys(n.children, inside)
        val seen = HashSet<String>()
        val tops = picked.filter { it.key !in inside && seen.add(it.key) }
        return ArrayList<Entry>().also { place(tops, "", it) }
    }

    private fun collectKeys(nodes: List<Node>, into: MutableSet<String>) {
        for (n in nodes) {
            into.add(n.key)
            if (n.isDir) collectKeys(n.children, into)
        }
    }

    private fun place(nodes: List<Node>, prefix: String, out: MutableList<Entry>) {
        val taken = HashSet<String>()
        for (n in nodes.sortedWith(compareBy<Node>({ !it.isDir }, { it.name.lowercase() }, { it.name }))) {
            if (n.isDir) {
                val dir = "$prefix${unique(segment(n.name), "", taken)}/"
                out.add(Entry(dir, null))
                place(n.children, dir, out)
            } else {
                out.add(Entry(prefix + unique(segment(DocumentKind.stripSuffix(n.name)), ".pdf", taken), n.source))
            }
        }
    }

    /** [stem] + [ext], numbered "stem (2)" and up while a sibling already has that name in any case. */
    private fun unique(stem: String, ext: String, taken: MutableSet<String>): String {
        var name = "$stem$ext"
        var n = 2
        while (!taken.add(name.lowercase())) name = "$stem (${n++})$ext"
        return name
    }

    /** [name] as a single path segment: no separators or control characters, and never "." or "..". */
    private fun segment(name: String): String {
        val s = buildString { for (c in name) append(if (c == '/' || c == '\\' || c < ' ') '_' else c) }
        return if (s.isBlank() || s == "." || s == "..") "_" else s
    }
}

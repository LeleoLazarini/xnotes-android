package com.xnotes.platform

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every note's tasks in one small file, so the list can show them all without opening a note.
 * A note writes its own entry whenever its tasks change, when it opens and when the app pauses.
 */
object TaskIndex {
    class Task(val page: Int, val text: String, val done: Boolean)
    class Note(val uri: String, val name: String, val tasks: List<Task>)

    private fun file(context: Context) = File(context.filesDir, "tasks.json")

    @Synchronized
    private fun load(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrDefault(JSONObject())

    @Synchronized
    fun put(context: Context, uri: String, name: String, tasks: List<Task>) {
        val all = load(context)
        if (tasks.isEmpty()) all.remove(uri)
        else all.put(uri, JSONObject().put("name", name).put("tasks", JSONArray().apply {
            tasks.forEach { put(JSONObject().put("page", it.page).put("text", it.text).put("done", it.done)) }
        }))
        runCatching { file(context).writeText(all.toString()) }
    }

    fun all(context: Context): List<Note> {
        val all = load(context)
        return all.keys().asSequence().map { k ->
            val o = all.getJSONObject(k)
            val arr = o.optJSONArray("tasks") ?: JSONArray()
            Note(k, o.optString("name"), (0 until arr.length()).map { i -> arr.getJSONObject(i).let { t -> Task(t.optInt("page"), t.optString("text"), t.optBoolean("done")) } })
        }.sortedBy { it.name.lowercase() }.toList()
    }
}

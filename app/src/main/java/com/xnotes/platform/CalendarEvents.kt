package com.xnotes.platform

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The tablet calendar's events in a span of time, read through the system calendar provider. */
object CalendarEvents {
    class Event(val title: String, val start: LocalDateTime, val allDay: Boolean)

    fun read(context: Context, year: Int): List<Event> {
        val zone = ZoneId.systemDefault()
        val from = LocalDateTime.of(year, 1, 1, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val to = LocalDateTime.of(year + 1, 1, 1, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val out = ArrayList<Event>()
        context.contentResolver.query(uri, arrayOf(Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY), null, null, Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0)?.trim().orEmpty().ifEmpty { continue }
                val allDay = c.getInt(2) == 1
                // An all-day event starts at midnight UTC; read in local time it could land a day early.
                val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), if (allDay) ZoneOffset.UTC else zone)
                out += Event(title, start, allDay)
            }
        }
        return out
    }
}

package com.xnotes.platform

import java.text.Normalizer
import java.util.Calendar

/**
 * Reads a date and time out of a note in Portuguese: "amanhã", "sexta", "30/10", "14h", "às 9",
 * "15:30". What it cannot find falls back to the next useful moment: today at the hour given (or
 * tomorrow if that has passed), and with nothing at all, tomorrow at 9.
 */
object WhenParser {
    private val weekdays = listOf(
        "domingo" to Calendar.SUNDAY, "segunda" to Calendar.MONDAY, "terca" to Calendar.TUESDAY, "quarta" to Calendar.WEDNESDAY,
        "quinta" to Calendar.THURSDAY, "sexta" to Calendar.FRIDAY, "sabado" to Calendar.SATURDAY,
    )

    fun parse(text: String, now: Calendar = Calendar.getInstance()): Calendar {
        val t = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val c = now.clone() as Calendar
        var dateSet = true
        when {
            Regex("\\bdepois de amanha\\b").containsMatchIn(t) -> c.add(Calendar.DAY_OF_YEAR, 2)
            Regex("\\bamanha\\b").containsMatchIn(t) -> c.add(Calendar.DAY_OF_YEAR, 1)
            Regex("\\bhoje\\b").containsMatchIn(t) -> Unit
            else -> dateSet = false
        }
        if (!dateSet) Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(t)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            if (d in 1..31 && mo in 1..12) {
                c.set(Calendar.MONTH, mo - 1)
                c.set(Calendar.DAY_OF_MONTH, d)
                val y = m.groupValues[3]
                if (y.isNotEmpty()) c.set(Calendar.YEAR, y.toInt().let { if (it < 100) 2000 + it else it })
                else if (c.get(Calendar.DAY_OF_YEAR) < now.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) c.add(Calendar.YEAR, 1)
                dateSet = true
            }
        }
        if (!dateSet) weekdays.firstOrNull { (name, _) -> Regex("\\b$name").containsMatchIn(t) }?.let { (_, day) ->
            var ahead = (day - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (ahead == 0) ahead = 7
            c.add(Calendar.DAY_OF_YEAR, ahead)
            dateSet = true
        }
        var hour = -1
        var minute = 0
        Regex("\\b(\\d{1,2})(?::|h)(\\d{2})\\b").find(t)?.let { hour = it.groupValues[1].toInt(); minute = it.groupValues[2].toInt() }
            ?: Regex("\\b(\\d{1,2})\\s?h(?:oras?)?\\b").find(t)?.let { hour = it.groupValues[1].toInt() }
            ?: Regex("\\bas (\\d{1,2})\\b").find(t)?.let { hour = it.groupValues[1].toInt() }
        if (hour !in 0..23 || minute !in 0..59) { hour = -1; minute = 0 }
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        when {
            hour >= 0 -> {
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)
                if (!dateSet && c.before(now)) c.add(Calendar.DAY_OF_YEAR, 1)
            }
            else -> {
                if (!dateSet) c.add(Calendar.DAY_OF_YEAR, 1)
                c.set(Calendar.HOUR_OF_DAY, 9)
                c.set(Calendar.MINUTE, 0)
            }
        }
        return c
    }
}

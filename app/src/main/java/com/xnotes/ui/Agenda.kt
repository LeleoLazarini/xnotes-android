package com.xnotes.ui

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Bookmark
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TextItem
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.platform.TemplateLibrary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * The dated planner "New agenda" makes: a cover, then for each month a calendar page followed by
 * one page per day. The order is fixed by the year alone, so the page of any date is arithmetic
 * ([pageOf]) and the note needs to remember nothing but [Document.agendaYear].
 *
 * Month and day pages draw their rulings from the bundled agenda templates; the words that change
 * (month name, day numbers, the date) are locked text boxes, so they can't be nudged by accident.
 * The month grid's geometry here mirrors `agenda-month.xtemplate`, which is what lets a tap on a
 * day cell find its date.
 */
internal object Agenda {
    private const val DAY_TEMPLATE_ID = "com.xnotes.agenda.day"
    private const val MONTH_TEMPLATE_ID = "com.xnotes.agenda.month"

    // agenda-month.xtemplate: 12 mm margin, the header band, then weekday names, then a 7 x 6 grid.
    private const val INSET_MM = 12.0
    private const val GRID_TOP_MM = 28.0 // the 20 mm band plus the 8 mm weekday row
    private const val WEEKS = 6

    private val PT = Locale("pt", "BR")
    private val INK = Rgba(40, 40, 48)
    private val SOFT = Rgba(110, 110, 120)

    /** The cover, then each month's calendar page and its days. */
    fun pageOf(year: Int, date: LocalDate): Int? {
        if (date.year != year) return null
        var index = 1
        for (m in 1 until date.monthValue) index += 1 + LocalDate.of(year, m, 1).lengthOfMonth()
        return index + date.dayOfMonth
    }

    private fun monthPage(year: Int, month: Int): Int {
        var index = 1
        for (m in 1 until month) index += 1 + LocalDate.of(year, m, 1).lengthOfMonth()
        return index
    }

    /** The page to open [doc] on: today's, when it is an agenda for this year. */
    fun todayPage(doc: Document): Int? {
        val year = doc.agendaYear ?: return null
        return pageOf(year, LocalDate.now())?.takeIf { it < doc.pages.size }
    }

    /**
     * The day a tap at [local] (page space) on page [pageIndex] lands on, when that page is one of
     * [doc]'s month calendars and the tap is inside a dated cell.
     */
    fun dayAt(doc: Document, pageIndex: Int, local: Pt): Int? {
        val year = doc.agendaYear ?: return null
        val month = (1..12).firstOrNull { monthPage(year, it) == pageIndex } ?: return null
        val page = doc.pages.getOrNull(pageIndex) ?: return null
        val g = Grid(page, doc.dpi)
        val col = ((local.x - g.x0) / g.cellW).toInt()
        val row = ((local.y - g.y0) / g.cellH).toInt()
        if (local.x < g.x0 || local.y < g.y0 || col !in 0..6 || row !in 0 until WEEKS) return null
        val first = LocalDate.of(year, month, 1)
        val day = row * 7 + col - firstColumn(first) + 1
        if (day !in 1..first.lengthOfMonth()) return null
        return pageOf(year, first.withDayOfMonth(day))
    }

    /** Sunday-first column of a date, as the template's weekday row runs. */
    private fun firstColumn(d: LocalDate) = d.dayOfWeek.value % 7

    private class Grid(page: Page, private val dpi: Int) {
        private fun mm(v: Number) = v.toDouble() / 25.4 * dpi
        val x0 = mm(INSET_MM)
        val y0 = mm(INSET_MM + GRID_TOP_MM)
        val cellW = (page.width - 2 * mm(INSET_MM)) / 7
        val cellH = (page.height - y0 - mm(INSET_MM)) / WEEKS
    }

    /** Fill [doc] (empty, with its dpi set) with the whole of [year], on pages [w] x [h]. */
    fun build(doc: Document, year: Int, w: Double, h: Double, measurer: TextMeasurer) {
        val dpi = doc.dpi
        fun mm(v: Number) = v.toDouble() / 25.4 * dpi
        fun key(id: String) = TemplateLibrary.all().firstOrNull { it.template.id == id }?.key
        val dayKey = key(DAY_TEMPLATE_ID)?.also { TemplateLibrary.embed(doc, it) }
        val monthKey = key(MONTH_TEMPLATE_ID)?.also { TemplateLibrary.embed(doc, it) }
        val inset = mm(INSET_MM)
        val textW = w - 2 * inset
        fun text(page: Page, x: Double, y: Double, s: String, pt: Double, face: FontFace = FontFace.SERIF, color: Rgba = INK, width: Double = textW) {
            page.items += TextItem(Pt(x, y), width, 0.0, s, color, pt, face, measurer).also { it.locked = true }
        }

        val cover = Page(w, h)
        text(cover, inset, h * 0.34, "Agenda", 44.0)
        text(cover, inset, h * 0.34 + mm(20), year.toString(), 96.0)
        doc.pages += cover

        val monthName = DateTimeFormatter.ofPattern("LLLL", PT)
        val dayTitle = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PT)
        for (m in 1..12) {
            val first = LocalDate.of(year, m, 1)
            val mp = Page(w, h, style = PageStyle(template = monthKey))
            text(mp, inset, inset + mm(3), "${monthName.format(first).cap()} de $year", 26.0)
            val g = Grid(mp, dpi)
            for (d in 1..first.lengthOfMonth()) {
                val idx = firstColumn(first) + d - 1
                text(mp, g.x0 + (idx % 7) * g.cellW + mm(1.5), g.y0 + (idx / 7) * g.cellH + mm(0.8), d.toString(), 13.0, FontFace.SANS, INK, g.cellW - mm(2))
            }
            doc.bookmarks += Bookmark(doc.pages.size, monthName.format(first).cap())
            doc.pages += mp
            for (d in 1..first.lengthOfMonth()) {
                val date = first.withDayOfMonth(d)
                val dp = Page(w, h, style = PageStyle(template = dayKey))
                text(dp, inset, inset + mm(3), dayTitle.format(date).cap(), 21.0, width = textW - mm(34))
                text(dp, w - inset - mm(32), inset + mm(5), "Semana ${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", 11.0, FontFace.SANS, SOFT, mm(32))
                doc.pages += dp
            }
        }
    }

    /**
     * Repaint the locked words on [pages] of an agenda [doc] in each page's heading colour (its own,
     * else the document's), or the default ink when none is set. False when [doc] is no agenda.
     */
    fun recolor(doc: Document, pages: List<Page>): Boolean {
        if (doc.agendaYear == null) return false
        for (p in pages) {
            val c = (p.style.accentColor ?: doc.style.accentColor)?.withAlpha(255) ?: INK
            for (item in p.items) if (item is TextItem && item.locked) item.rgba = c
        }
        return true
    }

    private val CAL = Rgba(37, 99, 235)

    /**
     * Write [events] on [doc]'s pages: on each day page the timed ones in their hour row (7h to
     * 22h) and the rest on a line under the date; on the month pages a few per cell. Whatever an
     * earlier refresh placed is removed first, so this can run on every opening.
     */
    fun placeEvents(doc: Document, events: List<com.xnotes.platform.CalendarEvents.Event>, measurer: TextMeasurer) {
        val year = doc.agendaYear ?: return
        val dpi = doc.dpi
        fun mm(v: Number) = v.toDouble() / 25.4 * dpi
        for (p in doc.pages) p.items.removeAll { it is TextItem && it.fromCalendar }
        fun put(page: Page, x: Double, y: Double, s: String, pt: Double, width: Double) {
            page.items += TextItem(Pt(x, y), width, 0.0, s, CAL, pt, FontFace.SANS, measurer).also { it.locked = true; it.fromCalendar = true }
        }
        val byDay = events.filter { it.start.year == year }.groupBy { it.start.toLocalDate() }
        val inset = mm(INSET_MM)
        for ((date, list) in byDay) {
            val dayPage = pageOf(year, date)?.let { doc.pages.getOrNull(it) } ?: continue
            val innerW = dayPage.width - 2 * inset
            val innerH = dayPage.height - 2 * inset
            val rowH = (innerH - mm(27)) / 16
            val (timed, other) = list.partition { !it.allDay && it.start.hour in 7..22 }
            for ((hour, inHour) in timed.groupBy { it.start.hour }) {
                val y = inset + mm(27) + (hour - 7) * rowH + mm(0.8)
                put(dayPage, inset + mm(14.5), y, inHour.joinToString("  ·  ") { "%02d:%02d %s".format(it.start.hour, it.start.minute, it.title) }, 9.5, innerW * 0.62 - mm(15))
            }
            if (other.isNotEmpty()) {
                put(dayPage, inset, inset + mm(11.5), other.joinToString("  ·  ") { e -> if (e.allDay) e.title else "%02d:%02d %s".format(e.start.hour, e.start.minute, e.title) }, 8.5, innerW - mm(34))
            }
            val monthPage = doc.pages.getOrNull(monthPage(year, date.monthValue)) ?: continue
            val g = Grid(monthPage, dpi)
            val idx = firstColumn(date.withDayOfMonth(1)) + date.dayOfMonth - 1
            val fit = ((g.cellH - mm(7)) / mm(3.3)).toInt().coerceAtLeast(1)
            val lines = list.map { it.title }
            val shown = if (lines.size <= fit) lines else lines.take(fit - 1) + "+${lines.size - fit + 1}"
            put(monthPage, g.x0 + (idx % 7) * g.cellW + mm(1.2), g.y0 + (idx / 7) * g.cellH + mm(6.2), shown.joinToString("\n"), 7.0, g.cellW - mm(2))
        }
    }

    private fun String.cap() = replaceFirstChar { it.titlecase(PT) }
}

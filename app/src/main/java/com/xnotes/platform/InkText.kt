package com.xnotes.platform

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Stroke
import kotlinx.coroutines.tasks.await

/**
 * Handwriting to text with ML Kit Digital Ink, in Brazilian Portuguese. The model (about 20 MB)
 * downloads once on first use and then works offline. Ink is read a line at a time: strokes are
 * grouped into lines by how they overlap vertically, each line goes to the recogniser on its own
 * with the line before as context, and the lines come back joined top to bottom.
 */
object InkText {
    private const val LANG = "pt-BR"

    private val model: DigitalInkRecognitionModel by lazy {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANG)
            ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("pt")!!
        DigitalInkRecognitionModel.builder(id).build()
    }
    private val recognizer: DigitalInkRecognizer by lazy {
        DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    }

    /** True once the Portuguese model is on the device. */
    suspend fun ready(): Boolean = runCatching { RemoteModelManager.getInstance().isModelDownloaded(model).await() }.getOrDefault(false)

    /** Fetch the model if it isn't here yet; false when that failed (no connection, say). */
    suspend fun ensureModel(): Boolean {
        if (ready()) return true
        return runCatching {
            RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build()).await()
            true
        }.getOrDefault(false)
    }

    /** One recognised line: its text, its alternatives, and where its ink sits on the page. */
    class Line(val text: String, val alternatives: List<String>, val bounds: Rect)

    /** [strokes] (page space, any order) as lines of text, top to bottom. Empty when nothing reads. */
    suspend fun recognize(strokes: List<Stroke>): List<Line> {
        if (strokes.isEmpty() || !ensureModel()) return emptyList()
        val out = ArrayList<Line>()
        var before = ""
        for (line in lines(strokes)) {
            val ink = Ink.builder()
            // Recognition reads stroke order, so strokes go left to right within the line, each on
            // its own clock offset so their times never overlap.
            var clock = 0L
            for (s in line.sortedBy { it.bounds().left }) {
                val sb = Ink.Stroke.builder()
                for (i in 0 until s.sampleCount) sb.addPoint(Ink.Point.create(s.xAt(i).toFloat(), s.yAt(i).toFloat(), clock + s.tAt(i).toLong()))
                clock += (s.tAt(s.sampleCount - 1).toLong() + STROKE_GAP_MS)
                ink.addStroke(sb.build())
            }
            val box = Rect.bounding(line.flatMap { s -> s.bounds().let { listOf(it.topLeft, com.xnotes.core.geometry.Pt(it.right, it.bottom)) } })
            val context = RecognitionContext.builder()
                .setPreContext(before.takeLast(20))
                .setWritingArea(WritingArea(box.w.toFloat().coerceAtLeast(1f), box.h.toFloat().coerceAtLeast(1f)))
                .build()
            val result = runCatching { recognizer.recognize(ink.build(), context).await() }.getOrNull() ?: continue
            val candidates = result.candidates.map { it.text }.filter { it.isNotBlank() }
            val best = candidates.firstOrNull() ?: continue
            out += Line(best, candidates.drop(1).take(4), box)
            before = "$before $best".trim()
        }
        return out
    }

    /** Strokes grouped into lines: a stroke joins the line it overlaps most vertically, else starts one. */
    fun lines(strokes: List<Stroke>): List<List<Stroke>> {
        val groups = ArrayList<MutableList<Stroke>>()
        val spans = ArrayList<DoubleArray>() // top, bottom of each line so far
        for (s in strokes.sortedBy { it.bounds().centerY }) {
            val b = s.bounds()
            var best = -1
            var bestOverlap = 0.0
            for (i in spans.indices) {
                val top = maxOf(spans[i][0], b.top)
                val bottom = minOf(spans[i][1], b.bottom)
                val overlap = (bottom - top) / minOf(spans[i][1] - spans[i][0], b.h).coerceAtLeast(1.0)
                if (overlap > bestOverlap) { bestOverlap = overlap; best = i }
            }
            if (best >= 0 && bestOverlap >= LINE_OVERLAP) {
                groups[best] += s
                spans[best][0] = minOf(spans[best][0], b.top)
                spans[best][1] = maxOf(spans[best][1], b.bottom)
            } else {
                groups += mutableListOf(s)
                spans += doubleArrayOf(b.top, b.bottom)
            }
        }
        return groups.sortedBy { g -> g.minOf { it.bounds().top } }
    }

    private const val STROKE_GAP_MS = 120L
    /** Share of the smaller height two spans must share to be one line. */
    private const val LINE_OVERLAP = 0.35
}

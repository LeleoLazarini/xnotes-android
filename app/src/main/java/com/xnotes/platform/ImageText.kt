package com.xnotes.platform

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The words in a picture (a photo, a scan, a screenshot), read on the device with ML Kit. */
object ImageText {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text in the image file at [path], block by block; null when it can't be read. */
    suspend fun read(path: String): String? {
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } ?: return null
        val result = runCatching { recognizer.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull() ?: return null
        return result.textBlocks.joinToString("\n\n") { it.text }.trim().ifEmpty { null }
    }
}

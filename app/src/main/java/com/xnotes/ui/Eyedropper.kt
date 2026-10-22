package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba

/**
 * The eyedropper, while armed: a layer over the canvas that takes the next tap, reads the colour
 * under it from what is on screen, and makes that the active toolbar colour. A banner says what to do.
 */
@Composable
internal fun EyedropperOverlay(editor: Editor) {
    if (!editor.eyedropperActive) return
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    editor.pickColorAt(at.x.toInt(), at.y.toInt())
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            stringResource(R.string.eyedropper_hint),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 72.dp)
                .background(Color(0xCC202124), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** The colour at ([x], [y]) of a snapshot, opaque. */
internal fun pixelAt(bmp: android.graphics.Bitmap, x: Int, y: Int): Rgba? {
    if (x !in 0 until bmp.width || y !in 0 until bmp.height) return null
    val p = bmp.getPixel(x, y)
    return Rgba((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF, 255)
}

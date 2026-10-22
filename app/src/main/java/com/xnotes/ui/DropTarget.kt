package com.xnotes.ui

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext

/** Pictures and/or text another app shared in. */
class SharedIn(val uris: List<android.net.Uri>, val text: String?)

/**
 * Lets pictures and text be dragged in from another app (split screen or a floating window) and
 * dropped onto the page where they are let go. The system asks the source app for read access to
 * what was dragged; it is read off-thread and the access is released once it has been copied in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun dropTarget(editor: Editor): Modifier {
    val activity = LocalContext.current as? Activity ?: return Modifier
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = remember(editor) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                val permissions = activity.requestDragAndDropPermissions(drag)
                // Drag coordinates are in the root view; the canvas starts at [origin] within it.
                editor.acceptDrop(drag.clipData, (drag.x - origin.x).toDouble(), (drag.y - origin.y).toDouble(), activity.contentResolver) {
                    permissions?.release()
                }
                return true
            }
        }
    }
    return Modifier
        .onGloballyPositioned { origin = it.positionInRoot() }
        .dragAndDropTarget(
            shouldStartDragAndDrop = { e -> e.mimeTypes().any { it.startsWith("image/") || it.startsWith("text/") } },
            target = target,
        )
}


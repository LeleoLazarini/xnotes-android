package com.xnotes.canvas

import android.view.MotionEvent

/**
 * Palm rejection. A hand resting on the glass while writing reaches the app as fingers: as a
 * two- or three-finger tap (undo / redo) or as a held finger (picking up whatever is under it,
 * which arms the select tool). Right after the pen has been on or above the screen, or when the
 * contact is as wide as a palm, finger contact does neither.
 */
object PalmGuard {
    @Volatile
    private var lastPenMs = 0L

    /** Note when the pen touches or hovers. */
    fun see(e: MotionEvent) {
        for (i in 0 until e.pointerCount) {
            if (e.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) {
                lastPenMs = android.os.SystemClock.uptimeMillis()
                return
            }
        }
    }

    /** The pen was on or above the screen a moment ago. */
    fun penRecent(): Boolean = android.os.SystemClock.uptimeMillis() - lastPenMs < PEN_QUIET_MS

    /** Finger contact now is most likely the writing hand, not a gesture. */
    fun palmLikely(e: MotionEvent, density: Float): Boolean {
        if (penRecent()) return true
        for (i in 0 until e.pointerCount) {
            if (e.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER && e.getTouchMajor(i) > PALM_WIDTH_DP * density) return true
        }
        return false
    }

    /** How long after the pen fingers still count as the resting hand. */
    private const val PEN_QUIET_MS = 2500L

    /** A contact this wide (dp) is a palm, not a fingertip. */
    private const val PALM_WIDTH_DP = 28f
}

package com.xnotes.core.model

/**
 * A recording made in a note: its number in the note and the file it lives in while the note is
 * open. Strokes drawn while it ran point back to it ([Stroke.audioClip]) with the moment they began.
 */
class AudioClip(val id: Int, val file: java.io.File)

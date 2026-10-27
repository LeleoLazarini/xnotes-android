package com.xnotes.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xnotes.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the screen, with the microphone when allowed, into Movies/xnotes as an MP4: the way to
 * send an explanation written by hand and spoken over. Runs as a foreground service, as Android
 * requires of screen capture; the notification and the app's own pill both stop it.
 */
class ScreenRecorder : Service() {
    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "com.xnotes.action.STOP_SCREEN_RECORDING"
        private const val CHANNEL = "screen-recording"
        private const val NOTE_ID = 7301
        private const val MAX_SIDE = 1920

        var running by mutableStateOf(false)
            private set
        var startedAt = 0L
            private set

        /** Bumped when a recording ends: [lastSaved] true when the video was kept. */
        var finishedCount by mutableIntStateOf(0)
            private set
        var lastSaved = false
            private set

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenRecorder::class.java).setAction(ACTION_STOP))
        }
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var output: Uri? = null
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val note = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (mic && Build.VERSION.SDK_INT >= 30) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTE_ID, note, types)
        } else {
            startForeground(NOTE_ID, note)
        }
        val ok = runCatching { begin(intent, mic) }.isSuccess
        if (!ok) { lastSaved = false; cleanUp(keep = false) }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent?, mic: Boolean) {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent = (if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent?.getParcelableExtra(EXTRA_DATA))
            ?: error("no projection")
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data) ?: error("no projection")
        projection = p
        p.registerCallback(object : MediaProjection.Callback() { override fun onStop() = finish() }, Handler(Looper.getMainLooper()))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(metrics.widthPixels, metrics.heightPixels))
        fun even16(v: Double) = (v.toInt() / 16) * 16
        val w = even16(metrics.widthPixels * scale)
        val h = even16(metrics.heightPixels * scale)

        val name = "xnotes-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/xnotes")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("no file")
        } else {
            Uri.fromFile(java.io.File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), name))
        }
        output = uri
        val fd = contentResolver.openFileDescriptor(uri, "w") ?: error("no file")
        pfd = fd

        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder = r
        if (mic) r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setOutputFile(fd.fileDescriptor)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (mic) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
        }
        r.setVideoSize(w, h)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(6_000_000)
        r.prepare()
        display = p.createVirtualDisplay("xnotes-recording", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
        startedAt = android.os.SystemClock.uptimeMillis()
        running = true
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        val stopped = runCatching { recorder?.stop() }.isSuccess && running
        lastSaved = stopped
        cleanUp(keep = stopped)
    }

    private fun cleanUp(keep: Boolean) {
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { pfd?.close() }
        output?.let { uri ->
            if (Build.VERSION.SDK_INT >= 29) {
                if (keep) runCatching { contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
                else runCatching { contentResolver.delete(uri, null, null) }
            }
        }
        recorder = null; display = null; projection = null; pfd = null; output = null
        running = false
        finishedCount++
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.screen_rec_channel), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ScreenRecorder::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_kit_pen)
            .setContentTitle(getString(R.string.screen_rec_on))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.audio_stop), stop).build())
            .build()
    }
}

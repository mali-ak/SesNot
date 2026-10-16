package com.azhulabs.sesnot.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.azhulabs.sesnot.shared.NoteTemplate
import com.azhulabs.sesnot.shared.RecordingMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Ön plan servisi olarak kayıt: ekran kapansa da sürer, Ongoing Activity ile saat yüzünde görünür.
 * AAC 16 kHz mono; 10 dakikalık parçalar biter bitmez TransferManager'a verilir.
 */
class RecorderService : Service() {

    data class State(
        val isRecording: Boolean = false,
        val elapsedMillis: Long = 0,
        val template: NoteTemplate = NoteTemplate.NOTE,
        val error: String? = null,
    )

    inner class LocalBinder : Binder() { val service get() = this@RecorderService }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var recorder: MediaRecorder? = null
    private lateinit var transfer: TransferManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var sessionId = ""
    private var sessionStart = 0L
    private var partIndex = 0
    private var partStart = 0L
    private var currentFile: File? = null

    override fun onCreate() {
        super.onCreate()
        transfer = TransferManager(this)
        transfer.flushQueue()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    fun start(template: NoteTemplate) {
        if (_state.value.isRecording) return
        sessionId = UUID.randomUUID().toString()
        sessionStart = System.currentTimeMillis()
        partIndex = 0
        _state.value = State(isRecording = true, template = template)
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            startPart()
        } catch (e: Exception) {
            _state.value = State(error = e.message ?: "Kayıt başlatılamadı")
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        ticker = scope.launch {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(elapsedMillis = now - sessionStart)
                if (now - partStart >= MAX_PART_MILLIS) {
                    finishPart(isLast = false)
                    partIndex++
                    runCatching { startPart() }.onFailure { stop(it.message) }
                }
            }
        }
    }

    fun stop(error: String? = null) {
        ticker?.cancel(); ticker = null
        if (_state.value.isRecording) finishPart(isLast = true)
        _state.value = State(error = error, template = _state.value.template)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startPart() {
        val file = File(recordingsDir(this), "$sessionId-$partIndex.m4a")
        val r = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(32_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = r
        currentFile = file
        partStart = System.currentTimeMillis()
    }

    private fun finishPart(isLast: Boolean) {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
        val file = currentFile ?: return
        val meta = RecordingMetadata(
            id = sessionId,
            template = _state.value.template,
            startedAtMillis = sessionStart,
            durationMillis = System.currentTimeMillis() - partStart,
            partIndex = partIndex,
            partCount = if (isLast) partIndex + 1 else 0,
        )
        transfer.enqueue(file, meta)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Kayıt", NotificationManager.IMPORTANCE_LOW))
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SesNot kaydediyor")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_btn_speak_now)
            .setStatus(Status.Builder().addTemplate("Kayıt sürüyor").build())
            .build()
            .apply(this)
        return builder.build()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.azhulabs.sesnot.wear.STOP"
        const val MAX_PART_MILLIS = 10 * 60 * 1000L
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1

        fun recordingsDir(ctx: Context): File = File(ctx.filesDir, "recordings").apply { mkdirs() }
    }
}

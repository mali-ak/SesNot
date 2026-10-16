package com.azhulabs.sesnot.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.azhulabs.sesnot.shared.Note
import com.azhulabs.sesnot.shared.NoteTemplate
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
 * Telefondan kayıt modu: saat uygulaması olmayan (ör. Huawei GT) kullanıcılar için.
 * Ses kaynağı olarak bağlı Bluetooth arama cihazının (saatin) mikrofonu seçilebilir; bu durumda
 * SCO kanalı açılır ve `VOICE_COMMUNICATION` kaynağı saat mikrofonuna yönlenir.
 * Kayıt bitince dosya doğrudan `ProcessingService`e verilir; saatten gelen kayıtla aynı yolu izler.
 */
class PhoneRecorderService : Service() {

    data class State(
        val isRecording: Boolean = false,
        val elapsedMillis: Long = 0,
        val template: NoteTemplate = NoteTemplate.NOTE,
        val useBluetoothMic: Boolean = false,
        val bluetoothDeviceName: String? = null,
        val error: String? = null,
    )

    inner class LocalBinder : Binder() { val service get() = this@PhoneRecorderService }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var noteId = ""
    private var startedAt = 0L
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val template = intent.getStringExtra(EXTRA_TEMPLATE)?.let { runCatching { NoteTemplate.valueOf(it) }.getOrNull() }
                    ?: NoteTemplate.NOTE
                start(template, intent.getBooleanExtra(EXTRA_BLUETOOTH, true))
            }
            ACTION_STOP -> stop()
            ACTION_TOGGLE -> if (_state.value.isRecording) stop() else start(NoteTemplate.NOTE, true)
        }
        return START_NOT_STICKY
    }

    /** Bağlı Bluetooth arama cihazı (HFP/SCO) varsa adını döndürür. */
    fun bluetoothMicDevice(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

    fun start(template: NoteTemplate, useBluetoothMic: Boolean) {
        if (_state.value.isRecording) return
        val btDevice = if (useBluetoothMic) bluetoothMicDevice() else null
        val usingBt = btDevice != null
        _state.value = State(isRecording = true, template = template, useBluetoothMic = usingBt,
            bluetoothDeviceName = btDevice?.productName?.toString(),
            error = if (useBluetoothMic && !usingBt) "Bluetooth mikrofon bulunamadı, telefon mikrofonu kullanılıyor" else null)

        startForeground(NOTIFICATION_ID, notification(template))
        try {
            if (usingBt) routeToBluetooth(btDevice!!)
            noteId = UUID.randomUUID().toString()
            startedAt = System.currentTimeMillis()
            val repo = (application as SesNotApp).repository
            val f = File(repo.recordingsDir, "$noteId.m4a")
            recorder = MediaRecorder(this).apply {
                // VOICE_COMMUNICATION: SCO açıkken sistem bu kaynağı Bluetooth mikrofona yönlendirir.
                setAudioSource(if (usingBt) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32_000)
                setOutputFile(f.absolutePath)
                if (usingBt && Build.VERSION.SDK_INT >= 28) setPreferredDevice(btDevice)
                prepare(); start()
            }
            file = f
            ticker = scope.launch {
                while (true) { delay(500); _state.value = _state.value.copy(elapsedMillis = System.currentTimeMillis() - startedAt) }
            }
        } catch (e: Exception) {
            releaseBluetooth()
            _state.value = State(error = e.message ?: "Kayıt başlatılamadı", template = template)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun stop() {
        ticker?.cancel(); ticker = null
        val r = recorder ?: return
        runCatching { r.stop() }; r.release(); recorder = null
        releaseBluetooth()
        val f = file; file = null
        val template = _state.value.template
        val duration = System.currentTimeMillis() - startedAt
        _state.value = State(template = template)
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (f != null && f.length() > 0 && duration > 1000) {
            val repo = (application as SesNotApp).repository
            repo.upsert(Note(id = noteId, template = template, createdAtMillis = startedAt, durationMillis = duration,
                status = Note.Status.RECEIVED, audioFileName = f.name))
            startForegroundService(Intent(this, ProcessingService::class.java).putExtra(ProcessingService.EXTRA_NOTE_ID, noteId))
        } else {
            f?.delete()
        }
    }

    private fun routeToBluetooth(device: AudioDeviceInfo) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.setCommunicationDevice(device)
        } else {
            @Suppress("DEPRECATION") audioManager.startBluetoothSco()
            @Suppress("DEPRECATION") audioManager.isBluetoothScoOn = true
        }
    }

    private fun releaseBluetooth() {
        if (!_state.value.useBluetoothMic) return
        if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
        else { @Suppress("DEPRECATION") audioManager.stopBluetoothSco(); @Suppress("DEPRECATION") audioManager.isBluetoothScoOn = false }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun notification(template: NoteTemplate): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Kayıt", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, PhoneRecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SesNot kaydediyor · ${template.title}")
            .setContentText(_state.value.bluetoothDeviceName?.let { "Mikrofon: $it" } ?: "Telefon mikrofonu")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { if (_state.value.isRecording) stop(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.azhulabs.sesnot.mobile.START"
        const val ACTION_STOP = "com.azhulabs.sesnot.mobile.STOP"
        const val ACTION_TOGGLE = "com.azhulabs.sesnot.mobile.TOGGLE"
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_BLUETOOTH = "bluetooth"
        private const val CHANNEL = "phone_recording"
        private const val NOTIFICATION_ID = 2

        fun toggleIntent(ctx: Context) = Intent(ctx, PhoneRecorderService::class.java).setAction(ACTION_TOGGLE)
    }
}

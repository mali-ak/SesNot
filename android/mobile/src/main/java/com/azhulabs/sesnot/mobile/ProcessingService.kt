package com.azhulabs.sesnot.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Transkripsiyon + özet dakikalar alabilir; ön plan servisi sistemin işi öldürmesini engeller. */
class ProcessingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var active = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID) ?: run { stopSelf(startId); return START_NOT_STICKY }
        startForeground(1, notification())
        active++
        scope.launch {
            (application as SesNotApp).pipeline.process(noteId)
            if (--active <= 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_REDELIVER_INTENT
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "İşleme", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("SesNot")
            .setContentText("Kayıt metne çevriliyor ve özetleniyor…")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
        private const val CHANNEL = "processing"
    }
}

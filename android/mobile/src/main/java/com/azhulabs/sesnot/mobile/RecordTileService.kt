package com.azhulabs.sesnot.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * Hızlı ayarlar kutucuğu: tek dokunuşla kayıt başlat/durdur (varsayılan şablon: Sesli Not,
 * Bluetooth mikrofon varsa o). Saati olmayan kullanıcı için en hızlı tetikleyici.
 */
class RecordTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = "SesNot"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // İzin yoksa uygulamayı aç; ilk kayıt oradan verilir.
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE))
            return
        }
        startForegroundService(PhoneRecorderService.toggleIntent(this))
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            subtitle = if (state == Tile.STATE_ACTIVE) "Kaydediyor" else null
            updateTile()
        }
    }
}

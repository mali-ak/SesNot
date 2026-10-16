package com.azhulabs.sesnot.mobile

import android.content.Intent
import android.util.Log
import com.azhulabs.sesnot.shared.Note
import com.azhulabs.sesnot.shared.RecordingMetadata
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Saattan açılan kanalı okur: ilk satır üst veri JSON, kalanı ses dosyası.
 * Tüm parçalar geldiğinde `ProcessingService` başlatılır (ön plan servisi, uzun işlem).
 */
class WearListenerService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != CHANNEL_PATH) return
        val app = application as SesNotApp
        val repo = app.repository
        runBlocking {
            try {
                val input = Wearable.getChannelClient(this@WearListenerService).getInputStream(channel).await()
                BufferedInputStream(input).use { ins ->
                    val header = ByteArrayOutputStream()
                    while (true) {
                        val b = ins.read(); if (b < 0 || b == '\n'.code) break
                        header.write(b)
                    }
                    val meta = RecordingMetadata.fromJson(header.toString("UTF-8"))
                        ?: run { Log.w(TAG, "üst veri okunamadı"); return@runBlocking }

                    val part = File(repo.partsDir, "${meta.id}-${meta.partIndex}.m4a")
                    part.outputStream().use { ins.copyTo(it) }
                    if (meta.partCount > 0) partCountPrefs().edit().putInt(meta.id, meta.partCount).apply()

                    // Not kaydını hemen oluştur: kullanıcı "alındı" durumunu görür.
                    if (repo.get(meta.id) == null) {
                        repo.upsert(Note(
                            id = meta.id, template = meta.template, createdAtMillis = meta.startedAtMillis,
                            durationMillis = meta.durationMillis, status = Note.Status.RECEIVED,
                            audioFileName = "${meta.id}.m4a",
                        ))
                    } else {
                        repo.update(meta.id) { it.copy(durationMillis = it.durationMillis + meta.durationMillis) }
                    }

                    val parts = repo.partsDir.listFiles { f -> f.name.startsWith(meta.id) && f.extension == "m4a" } ?: emptyArray()
                    val expected = partCountPrefs().getInt(meta.id, 0)
                    if (expected > 0 && parts.size >= expected) {
                        AudioMerger.merge(parts.sortedBy { it.name }, File(repo.recordingsDir, "${meta.id}.m4a"))
                        parts.forEach { it.delete() }
                        partCountPrefs().edit().remove(meta.id).apply()
                        startForegroundService(Intent(this@WearListenerService, ProcessingService::class.java)
                            .putExtra(ProcessingService.EXTRA_NOTE_ID, meta.id))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "kanal okuma hatası", e)
            } finally {
                runCatching { Wearable.getChannelClient(this@WearListenerService).close(channel).await() }
            }
        }
    }

    private fun partCountPrefs() = getSharedPreferences("partCounts", MODE_PRIVATE)

    companion object {
        const val CHANNEL_PATH = "/sesnot/recording"
        private const val TAG = "SesNotWear"
    }
}

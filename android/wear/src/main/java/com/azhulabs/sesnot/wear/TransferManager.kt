package com.azhulabs.sesnot.wear

import android.content.Context
import android.util.Log
import com.azhulabs.sesnot.shared.RecordingMetadata
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Saat → telefon aktarımı. Her dosya için bir ChannelClient kanalı açılır:
 *   1. ilk satır: üst veri JSON + '\n'
 *   2. ardından ses dosyasının baytları (sendFile)
 *
 * Telefon ulaşılamazsa dosya diskte kalır ve `flushQueue` (uygulama/servis açılışında) yeniden dener.
 * Üst veri dosyanın yanında `.json` olarak saklanır; böylece yeniden deneme bilgi kaybetmez.
 */
class TransferManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount
    private val _lastStatus = MutableStateFlow<String?>(null)
    val lastStatus: StateFlow<String?> = _lastStatus

    fun enqueue(file: File, meta: RecordingMetadata) {
        File(file.path + ".json").writeText(meta.toJson())
        refreshPending()
        scope.launch { send(file, meta) }
    }

    fun flushQueue() {
        refreshPending()
        scope.launch {
            RecorderService.recordingsDir(context).listFiles { f -> f.extension == "m4a" }?.forEach { f ->
                val meta = File(f.path + ".json").takeIf { it.exists() }?.let { RecordingMetadata.fromJson(it.readText()) }
                if (meta == null) { f.delete(); return@forEach }
                send(f, meta)
            }
        }
    }

    private suspend fun send(file: File, meta: RecordingMetadata) = lock.withLock {
        val nodeId = phoneNodeId() ?: run { _lastStatus.value = "Telefon bağlı değil, bekliyor"; return@withLock }
        val channelClient = Wearable.getChannelClient(context)
        try {
            val channel = channelClient.openChannel(nodeId, CHANNEL_PATH).await()
            channelClient.getOutputStream(channel).await().use { out ->
                out.write((meta.toJson() + "\n").toByteArray())
                out.flush()
                file.inputStream().use { it.copyTo(out) }
            }
            channelClient.close(channel).await()
            file.delete(); File(file.path + ".json").delete()
            _lastStatus.value = "Telefona gönderildi"
        } catch (e: Exception) {
            Log.w(TAG, "aktarım başarısız: ${file.name}", e)
            _lastStatus.value = "Aktarım hatası, yeniden denenecek"
        } finally {
            refreshPending()
        }
    }

    /** Telefon uygulamasının `sesnot_phone` yetkinliğini ilan eden düğümü bul. */
    private suspend fun phoneNodeId(): String? = runCatching {
        Wearable.getCapabilityClient(context)
            .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            .nodes.firstOrNull { it.isNearby }?.id
            ?: Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.id
    }.getOrNull()

    private fun refreshPending() {
        _pendingCount.value = RecorderService.recordingsDir(context).listFiles { f -> f.extension == "m4a" }?.size ?: 0
    }

    companion object {
        const val CHANNEL_PATH = "/sesnot/recording"
        const val PHONE_CAPABILITY = "sesnot_phone"
        private const val TAG = "SesNotTransfer"
    }
}

package com.azhulabs.sesnot.mobile

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhulabs.sesnot.shared.NoteTemplate

/**
 * Telefondan kayıt ekranı. Saat uygulaması olmayan kullanıcılar için: şablon seç, istersen
 * Bluetooth saat mikrofonunu kullan, kaydet. Bitince not listesine düşer ve işlenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var service by remember { mutableStateOf<PhoneRecorderService?>(null) }

    DisposableEffect(Unit) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as PhoneRecorderService.LocalBinder).service
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        val intent = Intent(ctx, PhoneRecorderService::class.java)
        ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        onDispose { ctx.unbindService(conn) }
    }

    var template by remember { mutableStateOf(NoteTemplate.NOTE) }
    var useBluetooth by remember { mutableStateOf(true) }
    val state by (service?.state?.collectAsState() ?: remember { mutableStateOf(PhoneRecorderService.State()) })
    val btDevice = service?.bluetoothMicDevice()

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) startRecording(ctx, template, useBluetooth)
    }

    fun toggle() {
        val s = service ?: return
        if (state.isRecording) { s.stop(); return }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startRecording(ctx, template, useBluetooth) else permissions.launch(needed.toTypedArray())
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Telefondan kaydet") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!state.isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteTemplate.entries.forEach { t ->
                        FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.title) })
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Saat mikrofonu (Bluetooth)", style = MaterialTheme.typography.bodyLarge)
                        Text(btDevice?.productName?.toString() ?: "Bağlı Bluetooth arama cihazı yok",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = useBluetooth && btDevice != null, onCheckedChange = { useBluetooth = it }, enabled = btDevice != null)
                }
            } else {
                Text(formatTime(state.elapsedMillis), fontSize = 48.sp)
                Text(state.template.title + (state.bluetoothDeviceName?.let { " · $it" } ?: " · Telefon mikrofonu"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(40.dp))
            FilledIconButton(
                onClick = { toggle() },
                modifier = Modifier.size(88.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            ) {
                Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(40.dp))
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Text("İpucu: Hızlı ayarlara \"SesNot kayıt\" kutucuğunu ekleyin; tek dokunuşla kayıt başlar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun startRecording(ctx: Context, template: NoteTemplate, useBluetooth: Boolean) {
    ctx.startForegroundService(Intent(ctx, PhoneRecorderService::class.java)
        .setAction(PhoneRecorderService.ACTION_START)
        .putExtra(PhoneRecorderService.EXTRA_TEMPLATE, template.name)
        .putExtra(PhoneRecorderService.EXTRA_BLUETOOTH, useBluetooth))
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

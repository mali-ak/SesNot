package com.azhulabs.sesnot.wear

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.azhulabs.sesnot.shared.NoteTemplate

class MainActivity : ComponentActivity() {

    private val service = mutableStateOf<RecorderService?>(null)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service.value = (binder as RecorderService.LocalBinder).service
        }
        override fun onServiceDisconnected(name: ComponentName) { service.value = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, RecorderService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        setContent { MaterialTheme { RecorderScreen(service.value) } }
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
}

@Composable
fun RecorderScreen(service: RecorderService?) {
    val state by (service?.state ?: return).collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val template = androidx.compose.runtime.remember { mutableStateOf(NoteTemplate.NOTE) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) service.start(template.value)
    }

    fun toggle() {
        if (state.isRecording) service.stop()
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            service.start(template.value)
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.isRecording) {
            Text(formatTime(state.elapsedMillis), fontSize = 30.sp)
            Text(state.template.title, fontSize = 11.sp, color = MaterialTheme.colors.onSurfaceVariant)
        } else {
            // Şablon seçimi: tıklayınca sıradakine geçer (saatte liste yerine hızlı döngü).
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val all = NoteTemplate.entries
                    template.value = all[(all.indexOf(template.value) + 1) % all.size]
                },
                label = { Text(template.value.title) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        Button(
            onClick = { toggle() },
            modifier = Modifier.padding(top = 10.dp).size(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (state.isRecording) Color(0xFFD32F2F) else MaterialTheme.colors.primary),
        ) { Text(if (state.isRecording) "■" else "●", fontSize = 22.sp) }

        state.error?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colors.error) }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

package com.azhulabs.sesnot.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.azhulabs.sesnot.shared.Note
import com.azhulabs.sesnot.shared.NoteTemplate
import com.azhulabs.sesnot.shared.Summary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SesNotApp
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                LaunchedEffect(Unit) { app.pipeline.resumeUnfinished() }
                NavHost(nav, startDestination = "list") {
                    composable("list") { NotesList(app.repository, onOpen = { nav.navigate("note/$it") }, onRecord = { nav.navigate("record") }) }
                    composable("record") { RecordScreen { nav.popBackStack() } }
                    composable("note/{id}") { entry ->
                        NoteDetail(app, entry.arguments?.getString("id") ?: "") { nav.popBackStack() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesList(repo: NoteRepository, onOpen: (String) -> Unit, onRecord: () -> Unit) {
    val notes by repo.notes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("SesNot") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onRecord) { Icon(Icons.Default.Mic, "Telefondan kaydet") }
        },
    ) { pad ->
        if (notes.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Henüz not yok", style = MaterialTheme.typography.titleMedium)
                Text("Saatinizden kayıt başlatın ya da sağ alttaki mikrofonla telefondan kaydedin.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.padding(pad)) {
            items(notes, key = { it.id }) { n ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(n.id) },
                    headlineContent = { Text(n.summary?.title?.ifEmpty { null } ?: n.template.title) },
                    supportingContent = {
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(n.createdAtMillis)) +
                             if (n.speakerCount > 1) "  ·  ${n.speakerCount} konuşmacı" else "")
                    },
                    trailingContent = { StatusBadge(n.status) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun StatusBadge(status: Note.Status) {
    when (status) {
        Note.Status.READY -> {}
        Note.Status.FAILED -> Text("!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        else -> CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetail(app: SesNotApp, id: String, onBack: () -> Unit) {
    val notes by app.repository.notes.collectAsStateWithLifecycle()
    val note = notes.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(note.summary?.title?.ifEmpty { null } ?: note.template.title, maxLines = 1) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            actions = {
                note.summary?.let { s ->
                    IconButton({
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s.plainText(note.template) + "\n\n— SesNot")
                        }, "Paylaş"))
                    }) { Icon(Icons.Default.Share, "Paylaş") }
                }
                IconButton({ scope.launch { app.pipeline.reprocess(id) } }) { Icon(Icons.Default.Refresh, "Yeniden özetle") }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            item { StatusSection(note) { scope.launch { app.pipeline.process(id) } } }
            note.summary?.let { s -> item { SummarySection(s, note, app) } }
            item {
                Text("Transkript", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                if (note.speakerCount > 1) {
                    note.segments.forEach { seg ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(seg.speakerLabel, fontWeight = FontWeight.SemiBold, color = speakerColor(seg.speaker))
                            Spacer(Modifier.width(8.dp))
                            Text(seg.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else Text(note.transcript.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                if (note.template != NoteTemplate.MEETING && note.status == Note.Status.READY) {
                    Text("Konuşmacıları ayır", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).clickable { scope.launch { app.pipeline.reprocess(id, withDiarization = true) } })
                }
            }
        }
    }
}

@Composable
private fun StatusSection(note: Note, retry: () -> Unit) {
    when (note.status) {
        Note.Status.READY -> {}
        Note.Status.FAILED -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(note.errorMessage ?: "Bilinmeyen hata", color = MaterialTheme.colorScheme.error)
            Text("Tekrar dene", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { retry() })
        }
        Note.Status.RECEIVED -> Text("Saatten alındı, bekliyor…", Modifier.padding(vertical = 8.dp))
        Note.Status.TRANSCRIBING -> Text("Metne çevriliyor…", Modifier.padding(vertical = 8.dp))
        Note.Status.SUMMARIZING -> Text("Özetleniyor…", Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SummarySection(s: Summary, note: Note, app: SesNotApp) {
    Column {
        if (s.abstract.isNotEmpty()) {
            Text("Özet", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(s.abstract, style = MaterialTheme.typography.bodyMedium)
        }
        if (s.items.isNotEmpty()) {
            Text(when (note.template) { NoteTemplate.MEETING -> "Kararlar"; NoteTemplate.SHOPPING -> "Liste"; NoteTemplate.NOTE -> "Anahtar noktalar" },
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            s.items.forEach { item ->
                if (note.template == NoteTemplate.SHOPPING) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(item.checked, onCheckedChange = { c ->
                            app.repository.update(note.id) { n ->
                                n.copy(summary = n.summary?.copy(items = n.summary!!.items.map { if (it.id == item.id) it.copy(checked = c) else it }))
                            }
                        })
                        Text(item.text)
                    }
                } else Text("• " + item.text, Modifier.padding(vertical = 2.dp))
            }
        }
        if (s.actions.isNotEmpty()) {
            Text("Yapılacaklar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            s.actions.forEach { a ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.padding(end = 6.dp))
                    Column { Text(a.text); a.owner?.let { Text(it, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
        if (s.dates.isNotEmpty()) {
            Text("Tarihler", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(s.dates.joinToString(" · "))
        }
    }
}

@Composable
private fun speakerColor(i: Int) = listOf(
    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error,
)[i % 4]

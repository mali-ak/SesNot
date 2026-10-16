package com.azhulabs.sesnot.mobile

import android.content.Context
import com.azhulabs.sesnot.shared.Note
import com.azhulabs.sesnot.shared.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import java.io.File

/** Notlar files/notes.json, sesler files/recordings/. iOS NoteStore ile aynı düzen. */
class NoteRepository(context: Context) {
    private val file = File(context.filesDir, "notes.json")
    val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }
    val partsDir: File = File(context.cacheDir, "parts").apply { mkdirs() }

    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes

    fun get(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(note: Note) {
        _notes.value = (_notes.value.filter { it.id != note.id } + note).sortedByDescending { it.createdAtMillis }
        save()
    }

    @Synchronized
    fun update(id: String, change: (Note) -> Note) {
        val cur = get(id) ?: return
        upsert(change(cur))
    }

    @Synchronized
    fun delete(id: String) {
        get(id)?.let { File(recordingsDir, it.audioFileName).delete() }
        _notes.value = _notes.value.filter { it.id != id }
        save()
    }

    fun audioFile(note: Note) = File(recordingsDir, note.audioFileName)

    private fun load(): List<Note> =
        runCatching { json.decodeFromString<List<Note>>(file.readText()) }.getOrDefault(emptyList())

    private fun save() {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(_notes.value))
        tmp.renameTo(file)
    }
}

package com.example.portfoliopilot

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// **bold** and __italic__ markup
// ---------------------------------------------------------------------------
private val markup = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__", RegexOption.DOT_MATCHES_ALL)

private fun AnnotatedString.Builder.appendStyled(text: String, bold: Boolean, italic: Boolean) {
    if (text.isEmpty()) return
    withStyle(
        SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null
        )
    ) { append(text) }
}

private fun AnnotatedString.Builder.appendMarked(
    text: String, showMarkers: Boolean, markerColor: Color, bold: Boolean, italic: Boolean
) {
    var last = 0
    for (m in markup.findAll(text)) {
        appendStyled(text.substring(last, m.range.first), bold, italic)
        val isBold = m.groups[1] != null
        val marker = if (isBold) "**" else "__"
        val inner = (m.groups[1] ?: m.groups[2])!!.value
        if (showMarkers) withStyle(SpanStyle(color = markerColor)) { append(marker) }
        appendMarked(inner, showMarkers, markerColor, bold || isBold, italic || !isBold)
        if (showMarkers) withStyle(SpanStyle(color = markerColor)) { append(marker) }
        last = m.range.last + 1
    }
    appendStyled(text.substring(last), bold, italic)
}

/** With [showMarkers] the text keeps its exact length (needed while editing); without it the markers are hidden. */
fun noteAnnotated(text: String, showMarkers: Boolean, markerColor: Color = Color.Gray): AnnotatedString =
    buildAnnotatedString { appendMarked(text, showMarkers, markerColor, false, false) }

/** Wraps the selected text in [marker] (or removes it if it is already wrapped). */
private fun toggleMarker(value: TextFieldValue, marker: String): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)
    val text = value.text
    if (start == end) {
        val newText = text.substring(0, start) + marker + marker + text.substring(end)
        return TextFieldValue(newText, TextRange(start + marker.length))
    }
    val selected = text.substring(start, end)
    return if (selected.length >= marker.length * 2 && selected.startsWith(marker) && selected.endsWith(marker)) {
        val inner = selected.substring(marker.length, selected.length - marker.length)
        TextFieldValue(text.substring(0, start) + inner + text.substring(end), TextRange(start, start + inner.length))
    } else {
        val wrapped = marker + selected + marker
        TextFieldValue(text.substring(0, start) + wrapped + text.substring(end), TextRange(start, start + wrapped.length))
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------
private enum class NoteMode { List, View, Edit }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(vm: PortfolioViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(NoteMode.List) }
    var openId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var draftTitle by remember { mutableStateOf("") }
    var draftContent by remember { mutableStateOf(TextFieldValue("")) }
    var confirmDelete by remember { mutableStateOf(false) }

    val note = vm.savedNotes.firstOrNull { it.id == openId }
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val transformation = remember(markerColor) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText =
                TransformedText(noteAnnotated(text.text, true, markerColor), OffsetMapping.Identity)
        }
    }

    fun startEdit(existing: Note?) {
        openId = existing?.id
        draftTitle = existing?.title.orEmpty()
        draftContent = TextFieldValue(existing?.content.orEmpty())
        mode = NoteMode.Edit
    }

    fun save(): Boolean {
        if (draftTitle.isBlank() && draftContent.text.isBlank()) return false
        openId = vm.saveNote(openId, draftTitle, draftContent.text)
        return true
    }

    fun goBack() {
        when (mode) {
            NoteMode.Edit -> {
                // Like Google Keep: leaving the editor keeps what you wrote.
                if (save()) mode = NoteMode.View else mode = NoteMode.List
            }
            NoteMode.View -> mode = NoteMode.List
            NoteMode.List -> vm.showNotepad = false
        }
    }

    BackHandler { goBack() }

    // A note that disappeared (deleted) while it was open: return to the list.
    LaunchedEffect(note, mode) {
        if (mode == NoteMode.View && note == null) mode = NoteMode.List
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (mode) {
                            NoteMode.List -> "My Notepad"
                            NoteMode.View -> "Note"
                            NoteMode.Edit -> if (openId == null) "New note" else "Edit note"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            if (mode == NoteMode.List) {
                ExtendedFloatingActionButton(
                    onClick = { startEdit(null) },
                    icon = { Icon(Icons.Default.NoteAdd, null) },
                    text = { Text("New note") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            when (mode) {
                NoteMode.List -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search notes…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    val filtered = vm.savedNotes.filter {
                        query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true)
                    }
                    if (filtered.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.EditNote, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(if (vm.savedNotes.isEmpty()) "No notes yet" else "No matching notes", fontWeight = FontWeight.Bold)
                            Text("Tap “New note” to write your first one.", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                            items(filtered, key = { it.id }) { n ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth().clickable { openId = n.id; mode = NoteMode.View }
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(n.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (n.content.isNotBlank()) {
                                            Text(n.plainContent(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(n.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(88.dp)) }
                        }
                    }
                }

                NoteMode.View -> {
                    if (note != null) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(note.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(note.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(noteAnnotated(note.content, false), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { startEdit(note) }) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Edit")
                                }
                                TextButton(onClick = { copyToClipboard(context, note.plainContent(), "Note copied") }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Copy")
                                }
                                TextButton(onClick = { shareText(context, "${note.title}\n\n${note.plainContent()}", note.title, "Share note") }) {
                                    Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Share")
                                }
                                TextButton(onClick = { confirmDelete = true }) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                NoteMode.Edit -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draftTitle,
                            onValueChange = { draftTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { draftContent = toggleMarker(draftContent, "**") }) { Icon(Icons.Default.FormatBold, "Bold") }
                            IconButton(onClick = { draftContent = toggleMarker(draftContent, "__") }) { Icon(Icons.Default.FormatItalic, "Italic") }
                            IconButton(onClick = {
                                copyToClipboard(context, noteAnnotated(draftContent.text, false).text, "Note copied")
                            }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                if (save()) {
                                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                                    mode = NoteMode.View
                                } else {
                                    Toast.makeText(context, "Write something first", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save")
                            }
                        }
                        Text(
                            "Select text, then tap B or I. Bold shows as **text**, italic as __text__.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedTextField(
                            value = draftContent,
                            onValueChange = { draftContent = it },
                            placeholder = { Text("Write your note…") },
                            visualTransformation = transformation,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete && note != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("“${note.title}” will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(note.id)
                    confirmDelete = false
                    openId = null
                    mode = NoteMode.List
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

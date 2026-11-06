package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.NoteOrganization
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import opentasks.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val NoteOrganizationSaver = Saver<NoteOrganization, String>(
    save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) },
)

/** Organization is part of the editor draft and commits through the ordinary note Save action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteOrganizationControls(
    model: NoteViewModel, noteId: String?, value: NoteOrganization,
    onChange: (NoteOrganization) -> Unit, enabled: Boolean, onOpenNote: (Note) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(noteId, value) { model.setEditorContext(noteId, value) }
    FlowRow(Modifier.fillMaxWidth()) {
        TextButton(onClick = { dialog = "organize" }, enabled = enabled) { Text(stringResource(Res.string.note_organize)) }
        TextButton(onClick = { dialog = "links" }, enabled = enabled) { Text(stringResource(Res.string.note_links)) }
        if (value.isPinned) Text(stringResource(Res.string.note_pinned), style = MaterialTheme.typography.labelSmall)
        if (value.isArchived) Text(stringResource(Res.string.note_archived), style = MaterialTheme.typography.labelSmall)
    }
    if (dialog == "organize") {
        val tags by model.tags.collectAsState()
        NoteOrganizationDialog(value, tags, onChange, model::createTag, onDismiss = { dialog = null })
    } else if (dialog == "links") {
        val relations by model.editorRelations.collectAsState()
        if (relations.context == null || relations.context?.noteId != noteId) {
            AlertDialog(onDismissRequest = { dialog = null }, text = { CircularProgressIndicator() }, confirmButton = {})
        } else NoteLinksDialog(value, relations, onChange, onDismiss = { dialog = null }, onOpenNote = {
            dialog = null
            onOpenNote(it)
        })
    }
}

@Composable
internal fun NoteOrganizationDialog(
    value: NoteOrganization, tags: List<Tag>, onChange: (NoteOrganization) -> Unit,
    createTag: suspend (String) -> String?, onDismiss: () -> Unit,
) {
    var tagName by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text(stringResource(Res.string.note_organize)) },
        text = {
            Column {
                Text(stringResource(Res.string.note_organization_save_hint), style = MaterialTheme.typography.bodySmall)
                NoteOptionRow(stringResource(Res.string.note_pinned), value.isPinned, !adding) { onChange(value.copy(isPinned = it)) }
                NoteOptionRow(stringResource(Res.string.note_archived), value.isArchived, !adding) { onChange(value.copy(isArchived = it)) }
                Text(stringResource(Res.string.note_tags), style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(tags, key = { it.id }) { tag ->
                        NoteOptionRow(tag.name, tag.id in value.tagIds, !adding) { checked ->
                            onChange(value.copy(tagIds = if (checked) (value.tagIds + tag.id).distinct() else value.tagIds - tag.id))
                        }
                    }
                }
                OutlinedTextField(tagName, { tagName = it; failed = false }, enabled = !adding, singleLine = true,
                    label = { Text(stringResource(Res.string.note_new_tag)) }, modifier = Modifier.fillMaxWidth())
                if (failed) Text(stringResource(Res.string.note_tag_save_error), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !adding && tagName.isNotBlank(), onClick = {
                    adding = true
                    scope.launch {
                        try {
                            val id = createTag(tagName)
                            failed = id == null
                            if (id != null) { onChange(value.copy(tagIds = (value.tagIds + id).distinct())); tagName = "" }
                        } finally { adding = false }
                    }
                }) { Text(stringResource(Res.string.note_add_tag)) }
                if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !adding) { Text(stringResource(Res.string.done)) } },
    )
}

@Composable
private fun NoteOptionRow(label: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked)
        .minimumInteractiveTargetSize(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NoteLinksDialog(
    value: NoteOrganization, relations: NoteRelations, onChange: (NoteOrganization) -> Unit,
    onDismiss: () -> Unit, onOpenNote: (Note) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.note_links)) },
        text = {
            LazyColumn {
                item { Text(stringResource(Res.string.note_organization_save_hint), style = MaterialTheme.typography.bodySmall) }
                item { Text(stringResource(Res.string.note_linked_notes), style = MaterialTheme.typography.titleSmall) }
                if (relations.linked.isEmpty()) item { Text(stringResource(Res.string.note_no_links)) }
                items(relations.linked, key = { "linked:${it.id}" }) { reference ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { reference.note?.let(onOpenNote) }, enabled = reference.note != null, modifier = Modifier.weight(1f)) {
                            Text(reference.title.ifBlank { stringResource(if (reference.note == null) Res.string.note_link_unavailable else Res.string.note_untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { onChange(value.copy(linkedNoteIds = value.linkedNoteIds - reference.id)) }) { Text(stringResource(Res.string.note_unlink)) }
                    }
                }
                item { Text(stringResource(Res.string.note_backlinks), style = MaterialTheme.typography.titleSmall) }
                if (relations.backlinks.isEmpty()) item { Text(stringResource(Res.string.note_no_backlinks)) }
                items(relations.backlinks, key = { "backlink:${it.id}" }) { reference ->
                    TextButton(onClick = { reference.note?.let(onOpenNote) }) {
                        Text(reference.title.ifBlank { stringResource(Res.string.note_untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item { Text(stringResource(Res.string.note_insert_link), style = MaterialTheme.typography.titleSmall) }
                items(relations.available, key = { "available:${it.id}" }) { reference ->
                    TextButton(onClick = { onChange(value.copy(linkedNoteIds = (value.linkedNoteIds + reference.id).distinct())) }) {
                        Text(reference.title.ifBlank { stringResource(Res.string.note_untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.done)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteLibraryControls(model: NoteViewModel, showSearch: Boolean = false) {
    val filter by model.libraryFilter.collectAsState()
    val tags by model.tags.collectAsState()
    val selectedTagName by model.selectedTagName.collectAsState()
    var viewMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = OpenTasksTheme.dimens.paddingMedium)) {
        if (showSearch) {
            val query by model.searchQuery.collectAsState()
            OutlinedTextField(query, { model.searchQuery.value = it }, singleLine = true,
                placeholder = { Text(stringResource(Res.string.notes_search_action)) }, modifier = Modifier.fillMaxWidth())
        }
        FlowRow {
            Box {
                TextButton(onClick = { viewMenu = true }) { Text(stringResource(filter.view.label())) }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    NoteLibraryView.entries.forEach { view ->
                        DropdownMenuItem(text = { Text(stringResource(view.label())) }, onClick = {
                            model.libraryFilter.value = filter.copy(view = view); viewMenu = false
                        })
                    }
                }
            }
            Box {
                TextButton(onClick = { tagMenu = true }) { Text(selectedTagName ?: stringResource(if (filter.tagId == null) Res.string.note_all_tags else Res.string.note_tag_filter), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.note_all_tags)) }, onClick = {
                        model.libraryFilter.value = filter.copy(tagId = null); tagMenu = false
                    })
                    tags.forEach { tag ->
                        DropdownMenuItem(text = { Text(tag.name) }, onClick = {
                            model.libraryFilter.value = filter.copy(tagId = tag.id); tagMenu = false
                        })
                    }
                }
            }
            if (filter != NoteLibraryFilter()) TextButton(onClick = { model.libraryFilter.value = NoteLibraryFilter() }) {
                Text(stringResource(Res.string.clear_filter))
            }
        }
    }
}

private fun NoteLibraryView.label(): StringResource = when (this) {
    NoteLibraryView.ACTIVE -> Res.string.note_view_active
    NoteLibraryView.PINNED -> Res.string.note_pinned
    NoteLibraryView.ARCHIVE -> Res.string.note_view_archive
    NoteLibraryView.RECENT -> Res.string.note_view_recent
    NoteLibraryView.UNTAGGED -> Res.string.note_view_untagged
    NoteLibraryView.UNFINISHED_TASKS -> Res.string.note_view_unfinished
}

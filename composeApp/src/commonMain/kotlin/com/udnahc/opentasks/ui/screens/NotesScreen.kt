package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.NoteListItem
import com.udnahc.opentasks.viewmodel.NoteViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.empty_notes
import opentasks.composeapp.generated.resources.notes
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotesScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Note) -> Unit,
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val notes by viewModel.noteListItems.collectAsState()
    NotesContent(
        notes = notes,
        onNoteClick = onNoteClick,
        onSettingsClick = onSettingsClick,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesContent(
    notes: List<NoteListItem>,
    onNoteClick: (Note) -> Unit,
    onSettingsClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val topBarHeight = dimens.topBarHeight + statusBarHeight

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.empty_notes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topBarHeight + dimens.paddingMedium,
                        bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge,
                    ),
                ) {
                    items(notes, key = { it.note.id }) { note ->
                        NoteCard(note = note, onClick = { onNoteClick(note.note) })
                    }
                }
            }
        }

        // Translucent Top bar overlay
        OpenTasksTopBar(
            title = stringResource(Res.string.notes),
            containerStyle = OpenTasksTopBarContainerStyle.Translucent,
            actions = {
                OpenTasksSettingsButton(onClick = onSettingsClick)
            },
        )
    }
}

@Composable
internal fun NoteCard(
    note: NoteListItem,
    onClick: () -> Unit,
) {
    val noteData = note.note
    val dimens = OpenTasksTheme.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingSmall),
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(dimens.paddingLarge)) {
            if (noteData.title.isNotBlank()) {
                Text(
                    text = noteData.title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            if (note.previewText.isNotBlank()) {
                Text(
                    text = note.previewText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            Text(
                text = note.updatedAtText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- Previews ------------------------------------------------------------------

internal val previewNotes = listOf(
    NoteListItem(
        note = Note(
            id = "preview-note-1",
            title = "Meeting Notes",
            content = "Discussed roadmap priorities for Q2 and assigned owners",
            createdAt = 1773619200000L,
            updatedAt = 1773619200000L,
        ),
        previewText = "Discussed roadmap priorities for Q2 and assigned owners",
        updatedAtText = "Mar 15 2026",
    ),
    NoteListItem(
        note = Note(
            id = "preview-note-2",
            title = "Shopping List",
            content = "Milk, eggs, bread, coffee beans",
            createdAt = 1773532800000L,
            updatedAt = 1773532800000L,
        ),
        previewText = "Milk, eggs, bread, coffee beans",
        updatedAtText = "Mar 14 2026",
    ),
    NoteListItem(
        note = Note(
            id = "preview-note-3",
            title = "",
            content = "Quick thought: look into Kotlin Notebooks for data exploration",
            createdAt = 1773446400000L,
            updatedAt = 1773446400000L,
        ),
        previewText = "Quick thought: look into Kotlin Notebooks for data exploration",
        updatedAtText = "Mar 13 2026",
    ),
)

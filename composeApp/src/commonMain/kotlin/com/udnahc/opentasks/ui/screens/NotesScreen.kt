package com.udnahc.opentasks.ui.screens

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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.utcMillisToLocalMillis
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.TaskViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.empty_notes
import opentasks.composeapp.generated.resources.notes
import org.jetbrains.compose.resources.stringResource

private val MONTH_NAMES_SHORT = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private fun formatNoteDate(utcMillis: Long): String {
    if (utcMillis == 0L) return ""
    val local = utcMillisToLocalMillis(utcMillis)
    val y = extractYear(local)
    val m = extractMonth(local)
    val d = extractDay(local)
    return "$d ${MONTH_NAMES_SHORT[m - 1]} $y"
}

@Composable
fun NotesScreen(
    viewModel: TaskViewModel,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.notes.collectAsState()
    NotesContent(
        notes = notes,
        onNoteClick = onNoteClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesContent(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                items(notes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = { onNoteClick(note) })
                }
            }
        }

        // Translucent Top bar overlay
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
            ),
            title = {
                Text(
                    text = stringResource(Res.string.notes),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
) {
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
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            if (note.content.isNotBlank()) {
                val preview = remember(note.content) {
                    note.content
                        .replace(Regex("[#*_~`>\\-\\[\\]()]"), "")
                        .trim()
                        .lines()
                        .firstOrNull()
                        ?: ""
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.spacerSmall))
            }
            Text(
                text = formatNoteDate(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

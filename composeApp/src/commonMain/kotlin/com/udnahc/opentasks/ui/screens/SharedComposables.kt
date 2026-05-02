package com.udnahc.opentasks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.complete_recurring_task_title
import opentasks.composeapp.generated.resources.complete_series
import opentasks.composeapp.generated.resources.complete_this_occurrence
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_dropdown
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Empty placeholder ────────────────────────────────────────────────────────

/**
 * Generic centered text placeholder. Used for empty task lists and empty calendar days.
 *
 * @param text The message to display.
 * @param modifier Modifier for the outer Box (callers control size and padding).
 */
@Composable
fun EmptyPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Collapsible section ──────────────────────────────────────────────────────

/**
 * A card-based collapsible section with a header row (label + count + chevron)
 * and animated content that expands/collapses.
 *
 * @param label Section header text.
 * @param count Item count shown next to the chevron.
 * @param isCollapsed Whether the content is currently hidden.
 * @param onToggle Called when the header is clicked.
 * @param headerCardModifier Modifier applied to the header Card (e.g. horizontal padding).
 * @param contentCardModifier Modifier applied to the content Card.
 * @param labelColor Color for the header label text.
 * @param content Slot for the expandable content rendered inside the bottom-rounded Card.
 */
@Composable
fun CollapsibleSection(
    label: String,
    count: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    headerCardModifier: Modifier = Modifier,
    contentCardModifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens

    // Header card — fully rounded when collapsed, top-rounded when expanded
    Card(
        modifier = headerCardModifier.fillMaxWidth(),
        shape = if (isCollapsed) {
            RoundedCornerShape(dimens.cornerXLarge)
        } else {
            RoundedCornerShape(topStart = dimens.cornerXLarge, topEnd = dimens.cornerXLarge)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        CollapsibleSectionHeader(
            label = label,
            count = count,
            isCollapsed = isCollapsed,
            onClick = onToggle,
            labelColor = labelColor,
        )
    }

    // Content card — bottom-rounded, animated
    AnimatedVisibility(
        visible = !isCollapsed,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Card(
            modifier = contentCardModifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                bottomStart = dimens.cornerXLarge,
                bottomEnd = dimens.cornerXLarge,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    label: String,
    count: Int,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    labelColor: Color,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = labelColor,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(dimens.spacerSmall))
        Icon(
            painter = painterResource(
                if (isCollapsed) Res.drawable.ic_chevron_right
                else Res.drawable.ic_dropdown
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconMedium),
        )
    }
}

// ── Complete-series dialog ──────────────────────────────────────────────────

@Composable
fun CompleteSeriesDialog(
    onCompleteOccurrence: () -> Unit,
    onCompleteSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.complete_recurring_task_title)) },
        text = {
            Column {
                TextButton(onClick = onCompleteOccurrence, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.complete_this_occurrence))
                }
                TextButton(onClick = onCompleteSeries, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.complete_series))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}

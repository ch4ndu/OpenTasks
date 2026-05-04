package com.udnahc.opentasks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.complete_recurring_task_title
import opentasks.composeapp.generated.resources.complete_series
import opentasks.composeapp.generated.resources.complete_this_occurrence
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_dropdown
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.more
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Top app bar ─────────────────────────────────────────────────────────────

internal enum class OpenTasksTopBarContainerStyle {
    Default,
    Transparent,
    Translucent,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenTasksTopBar(
    title: String = "",
    modifier: Modifier = Modifier,
    containerStyle: OpenTasksTopBarContainerStyle = OpenTasksTopBarContainerStyle.Default,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    OpenTasksTopBar(
        titleContent = {
            OpenTasksTopBarTitle(title)
        },
        modifier = modifier,
        containerStyle = containerStyle,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenTasksTopBar(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerStyle: OpenTasksTopBarContainerStyle = OpenTasksTopBarContainerStyle.Default,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val containerColor = when (containerStyle) {
        OpenTasksTopBarContainerStyle.Default -> MaterialTheme.colorScheme.background
        OpenTasksTopBarContainerStyle.Transparent -> Color.Transparent
        OpenTasksTopBarContainerStyle.Translucent -> MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
    }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        navigationIcon = navigationIcon,
        title = titleContent,
        actions = actions,
    )
}

@Composable
internal fun OpenTasksTopBarTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
internal fun OpenTasksBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OpenTasksTopBarIconButton(
        icon = Res.drawable.ic_arrow_back,
        contentDescription = stringResource(Res.string.back),
        onClick = onClick,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun OpenTasksCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OpenTasksTopBarIconButton(
        icon = Res.drawable.ic_close,
        contentDescription = stringResource(Res.string.close),
        onClick = onClick,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun OpenTasksSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OpenTasksTopBarIconButton(
        icon = Res.drawable.ic_settings,
        contentDescription = stringResource(Res.string.settings),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun OpenTasksOverflowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OpenTasksTopBarIconButton(
        icon = Res.drawable.ic_more_vert,
        contentDescription = stringResource(Res.string.more),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun OpenTasksTopBarIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

// ── Dialog buttons ──────────────────────────────────────────────────────────

@Composable
internal fun PrimaryDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = text, color = PrimaryBlue)
    }
}

@Composable
internal fun DialogCancelTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = stringResource(Res.string.cancel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DialogDoneTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryDialogTextButton(
        text = stringResource(Res.string.done),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun DialogOkTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryDialogTextButton(
        text = stringResource(Res.string.ok),
        onClick = onClick,
        modifier = modifier,
    )
}

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
            DialogCancelTextButton(onClick = onDismiss)
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

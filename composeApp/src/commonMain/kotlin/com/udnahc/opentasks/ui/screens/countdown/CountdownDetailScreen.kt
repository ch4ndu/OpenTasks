package com.udnahc.opentasks.ui.screens.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import com.udnahc.opentasks.ui.theme.isReimagined
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
import opentasks.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.domain.usecase.countdown.CountdownOccurrence
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.ui.screens.OpenTasksBackButton
import com.udnahc.opentasks.ui.screens.ModalBusyEffect
import com.udnahc.opentasks.ui.screens.OpenTasksOverflowButton
import com.udnahc.opentasks.ui.screens.OpenTasksTopBar
import com.udnahc.opentasks.ui.screens.OpenTasksTopBarContainerStyle
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.formatLocalizedDateWithWeekday
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.countdown_days_since
import opentasks.composeapp.generated.resources.countdown_days_until
import opentasks.composeapp.generated.resources.countdown_detail_subtitle
import opentasks.composeapp.generated.resources.countdown_today_is
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.edit
import opentasks.composeapp.generated.resources.loading
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

@Composable
fun CountdownDetailScreen(
    countdown: CountdownOccurrence?,
    isBusy: Boolean = false,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    CountdownDetailContent(
        countdown = countdown,
        isBusy = isBusy,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = onDelete,
        onModalBusyChanged = onModalBusyChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownDetailContent(
    isBusy: Boolean = false,
    countdown: CountdownOccurrence?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onModalBusyChanged: (Boolean) -> Unit = {},
) {
    if (isReimagined && countdown != null) {
        ReimaginedCountdownDetail(countdown, isBusy, onBack, onEdit, onDelete, onModalBusyChanged)
        return
    }
    val dimens = OpenTasksTheme.dimens
    var showMenu by remember { mutableStateOf(false) }
    ModalBusyEffect(showMenu || isBusy, onModalBusyChanged)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            OpenTasksTopBar(
                title = "",
                containerStyle = OpenTasksTopBarContainerStyle.Translucent,
                navigationIcon = {
                    OpenTasksBackButton(onClick = { if (!isBusy) onBack() })
                },
                actions = {
                    Box {
                        OpenTasksOverflowButton(onClick = { if (!isBusy) showMenu = true })
                        DropdownMenu(
                            expanded = showMenu && !isBusy,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                },
            )

            if (countdown != null) {
                val source = countdown.countdown
                val daysLeft = countdown.daysUntil
                val isCountUp = source.countingMode == CountingMode.COUNT_UP
                val displayDays = abs(daysLeft)
                val subtitlePrefix = when {
                    isCountUp && daysLeft < 0 -> stringResource(Res.string.countdown_days_since)
                    isCountUp -> stringResource(Res.string.countdown_days_until)
                    daysLeft < 0 -> stringResource(Res.string.countdown_days_since)
                    daysLeft == 0 -> stringResource(Res.string.countdown_today_is)
                    else -> stringResource(Res.string.countdown_days_until)
                }
                val dateLabel = remember(countdown.effectiveDate) {
                    formatLocalizedDateWithWeekday(countdown.effectiveDate)
                }
                val typeColor = countdownTypeColor(source.countdownType)

                Spacer(Modifier.height(dimens.paddingXXLarge))

                // Centered detail card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.paddingXXLarge),
                    shape = RoundedCornerShape(dimens.cornerXLarge),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimens.paddingXXLarge,
                                vertical = dimens.paddingXXLarge + dimens.paddingXLarge,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Subtitle combines the localized relative prefix and platform date label.
                        Text(
                            text = stringResource(
                                Res.string.countdown_detail_subtitle,
                                subtitlePrefix,
                                dateLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(dimens.paddingXLarge))

                        // Large day count
                        Text(
                            text = if (daysLeft == 0) "0" else displayDays.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isReimagined) MaterialTheme.colorScheme.primary else PrimaryBlue,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(dimens.paddingLarge))

                        // Countdown name
                        Text(
                            text = source.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = typeColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -- Previews ------------------------------------------------------------------

private val previewCountdownSource = com.udnahc.opentasks.data.model.Countdown(
    id = "preview-detail",
    title = "Project Launch",
    targetDate = 1775088000000L,
    countdownType = CountdownType.COUNTDOWN,
    countingMode = CountingMode.COUNTDOWN,
)

internal val previewCountdown = CountdownOccurrence(
    countdown = previewCountdownSource,
    effectiveTargetDate = 1775088000000L,
    effectiveDate = LocalDate(2026, 4, 1),
    daysUntil = 90,
)


@Composable
private fun ReimaginedCountdownDetail(item: CountdownOccurrence, isBusy: Boolean, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onModalBusyChanged: (Boolean) -> Unit) {
    val d = OpenTasksTheme.dimens
    val source = item.countdown
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBusyEffect(confirmDelete || isBusy, onModalBusyChanged)
    val dateLabel = remember(item.effectiveDate) { formatLocalizedDateWithWeekday(item.effectiveDate) }
    val originalDateLabel = remember(source.targetDate) {
        formatLocalizedDateWithWeekday(com.udnahc.opentasks.data.extensions.localMillisToLocalDate(source.targetDate))
    }
    val reminders = remember(source.reminders) { source.reminders.toCountdownReminderSet() }
    val relative = when {
        item.daysUntil == 0 -> Res.string.today
        source.countingMode == CountingMode.COUNT_UP && item.daysUntil < 0 -> Res.string.countdown_days_since
        source.countingMode == CountingMode.COUNT_UP -> Res.string.countdown_days_until
        item.daysUntil < 0 -> Res.string.countdown_days_ago
        else -> Res.string.countdown_days_left
    }
    val hero: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(d.paddingXXLarge), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (item.daysUntil == 0) stringResource(Res.string.today) else abs(item.daysUntil).toString(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            if (item.daysUntil != 0) Text(stringResource(relative), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(d.paddingXLarge))
            Text(source.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(dateLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val fields: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(d.paddingXLarge), verticalArrangement = Arrangement.spacedBy(d.spacerLarge)) {
            CountdownDetailField(stringResource(Res.string.date), originalDateLabel, onEdit, !isBusy)
            CountdownDetailField(stringResource(Res.string.countdown_type), stringResource(countdownTypeLabelRes(source.countdownType)), onEdit, !isBusy)
            CountdownDetailField(stringResource(Res.string.countdown_counting_mode), stringResource(countingModeLabelRes(source.countingMode)), onEdit, !isBusy)
            CountdownDetailField(stringResource(Res.string.repeat), stringResource(recurrenceLabelRes(source.recurrenceType)), onEdit, !isBusy)
            Text(stringResource(Res.string.reminder), style = MaterialTheme.typography.labelLarge)
            if (reminders.isEmpty()) Text(stringResource(Res.string.none), style = MaterialTheme.typography.bodyLarge)
            reminders.forEach { reminder -> Text(stringResource(reminder.labelRes), style = MaterialTheme.typography.bodyLarge) }
            CountdownDetailField(stringResource(Res.string.countdown_smart_list), stringResource(smartListLabelRes(source.smartListVisibility)), onEdit, !isBusy)
            Row {
                Button(onClick = onEdit, enabled = !isBusy) { Text(stringResource(Res.string.edit)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }, enabled = !isBusy) { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        OpenTasksTopBar(title = stringResource(Res.string.countdown_title), navigationIcon = { OpenTasksBackButton(onClick = { if (!isBusy) onBack() }) })
        if (isBusy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        if (OpenTasksTheme.windowSizeCategory == WindowSizeCategory.COMPACT) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) { hero(); HorizontalDivider(); fields() }
        } else {
            Row(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Box(Modifier.weight(1f)) { hero() }
                Box(Modifier.weight(1f)) { fields() }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!isBusy) confirmDelete = false },
        title = { Text(stringResource(Res.string.countdown_delete_title)) },
        text = { Text(stringResource(Res.string.countdown_delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !isBusy) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !isBusy) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun CountdownDetailField(label: String, value: String, onEdit: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

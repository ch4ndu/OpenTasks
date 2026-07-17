package com.udnahc.opentasks.ui.screens.countdown

import androidx.compose.foundation.background
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
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    CountdownDetailContent(
        countdown = countdown,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownDetailContent(
    countdown: CountdownOccurrence?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var showMenu by remember { mutableStateOf(false) }

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
                    OpenTasksBackButton(onClick = onBack)
                },
                actions = {
                    Box {
                        OpenTasksOverflowButton(onClick = { showMenu = true })
                        DropdownMenu(
                            expanded = showMenu,
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
                            color = PrimaryBlue,
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

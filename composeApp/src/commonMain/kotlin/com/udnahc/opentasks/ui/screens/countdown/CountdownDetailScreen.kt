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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.countdown_days_since
import opentasks.composeapp.generated.resources.countdown_days_until
import opentasks.composeapp.generated.resources.countdown_today_is
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.edit
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_more_vert
import opentasks.composeapp.generated.resources.loading
import opentasks.composeapp.generated.resources.more
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val DAY_NAMES = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTH_NAMES_SHORT = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private fun formatTargetDateLabel(localMillis: Long): String {
    val date = localMillisToLocalDate(localMillis)
    val dayName = DAY_NAMES[date.dayOfWeek.ordinal]
    return "$dayName, ${date.year}.${date.monthNumber.toString().padStart(2, '0')}.${date.dayOfMonth.toString().padStart(2, '0')}"
}

@Composable
fun CountdownDetailScreen(
    countdown: Countdown?,
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
    countdown: Countdown?,
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {},
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_more_vert),
                                contentDescription = stringResource(Res.string.more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                val daysLeft = computeDaysUntil(countdown.targetDate)
                val isCountUp = countdown.countingMode == CountingMode.COUNT_UP
                val displayDays = abs(daysLeft)
                val subtitlePrefix = when {
                    isCountUp && daysLeft < 0 -> stringResource(Res.string.countdown_days_since)
                    isCountUp -> stringResource(Res.string.countdown_days_until)
                    daysLeft < 0 -> stringResource(Res.string.countdown_days_since)
                    daysLeft == 0 -> stringResource(Res.string.countdown_today_is)
                    else -> stringResource(Res.string.countdown_days_until)
                }
                val dateLabel = formatTargetDateLabel(countdown.targetDate)
                val typeColor = countdownTypeColor(countdown.countdownType)

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
                        // Subtitle: "Days until Thu, 2026.04.02"
                        Text(
                            text = "$subtitlePrefix $dateLabel",
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
                            text = countdown.title,
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

internal val previewCountdown = Countdown(
    id = "preview-detail",
    title = "Project Launch",
    targetDate = 1775088000000L,
    countdownType = CountdownType.COUNTDOWN,
    countingMode = CountingMode.COUNTDOWN,
)


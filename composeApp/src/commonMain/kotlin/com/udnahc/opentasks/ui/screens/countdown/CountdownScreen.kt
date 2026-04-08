package com.udnahc.opentasks.ui.screens.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.PriorityHigh
import com.udnahc.opentasks.ui.theme.PriorityMedium
import com.udnahc.opentasks.ui.theme.PriorityNone
import com.udnahc.opentasks.viewmodel.CountdownViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.countdown_counting_count_up
import opentasks.composeapp.generated.resources.countdown_counting_countdown
import opentasks.composeapp.generated.resources.countdown_days_ago
import opentasks.composeapp.generated.resources.countdown_days_left
import opentasks.composeapp.generated.resources.countdown_days_since
import opentasks.composeapp.generated.resources.countdown_days_until
import opentasks.composeapp.generated.resources.countdown_filter_all
import opentasks.composeapp.generated.resources.countdown_filter_anniversary
import opentasks.composeapp.generated.resources.countdown_filter_birthday
import opentasks.composeapp.generated.resources.countdown_filter_countdown
import opentasks.composeapp.generated.resources.countdown_filter_holiday
import opentasks.composeapp.generated.resources.countdown_no_items
import opentasks.composeapp.generated.resources.countdown_title
import opentasks.composeapp.generated.resources.ic_settings
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

// ---- Type colors ----

private val TypeColorHoliday = PriorityHigh
private val TypeColorBirthday = PrimaryBlue
private val TypeColorAnniversary = PriorityMedium
private val TypeColorCountdown = PriorityNone

internal fun countdownTypeColor(type: CountdownType): Color = when (type) {
    CountdownType.HOLIDAY -> TypeColorHoliday
    CountdownType.BIRTHDAY -> TypeColorBirthday
    CountdownType.ANNIVERSARY -> TypeColorAnniversary
    CountdownType.COUNTDOWN -> TypeColorCountdown
}

internal fun countdownTypeLabelRes(type: CountdownType): StringResource = when (type) {
    CountdownType.HOLIDAY -> Res.string.countdown_filter_holiday
    CountdownType.BIRTHDAY -> Res.string.countdown_filter_birthday
    CountdownType.ANNIVERSARY -> Res.string.countdown_filter_anniversary
    CountdownType.COUNTDOWN -> Res.string.countdown_filter_countdown
}

internal fun countdownTypeInitial(type: CountdownType): String = when (type) {
    CountdownType.HOLIDAY -> "H"
    CountdownType.BIRTHDAY -> "B"
    CountdownType.ANNIVERSARY -> "A"
    CountdownType.COUNTDOWN -> "C"
}

internal fun computeDaysUntil(targetDateLocalMillis: Long): Int {
    val today = todayLocal()
    val targetDate = localMillisToLocalDate(targetDateLocalMillis)
    return today.until(targetDate, DateTimeUnit.DAY).toInt()
}

@Composable
fun CountdownScreen(
    viewModel: CountdownViewModel,
    onCountdownClick: (Countdown) -> Unit,
    onDeleteCountdown: (Countdown) -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val countdowns by viewModel.filteredCountdowns.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    CountdownContent(
        countdowns = countdowns,
        selectedFilter = selectedFilter,
        onFilterSelected = viewModel::selectFilter,
        onCountdownClick = onCountdownClick,
        onSettingsClick = onSettingsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownContent(
    countdowns: List<Countdown>,
    selectedFilter: CountdownType?,
    onFilterSelected: (CountdownType?) -> Unit,
    onCountdownClick: (Countdown) -> Unit,
    onSettingsClick: () -> Unit = {},
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
        if (countdowns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.countdown_no_items),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + dimens.paddingMedium + dimens.touchTargetLarge + dimens.paddingMedium,
                    bottom = navBarHeight + dimens.fabAreaBottom + dimens.paddingXLarge,
                ),
            ) {
                items(countdowns, key = { it.id }) { countdown ->
                    CountdownCard(
                        countdown = countdown,
                        onClick = { onCountdownClick(countdown) },
                    )
                }
            }
        }

        // Filter chips row — below top bar
        Column {
            Spacer(Modifier.height(topBarHeight))
            FilterChipRow(
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
            )
        }

        // Translucent Top bar overlay
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
            ),
            title = {
                Text(
                    text = stringResource(Res.string.countdown_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = stringResource(Res.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun FilterChipRow(
    selectedFilter: CountdownType?,
    onFilterSelected: (CountdownType?) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    ScrollableTabRow(
        selectedTabIndex = if (selectedFilter == null) 0 else selectedFilter.ordinal + 1,
        containerColor = Color.Transparent,
        edgePadding = dimens.paddingXLarge,
        indicator = {},
        divider = {},
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text(stringResource(Res.string.countdown_filter_all)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                selectedLabelColor = PrimaryBlue,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.padding(end = dimens.spacerSmall),
        )
        CountdownType.entries.forEach { type ->
            FilterChip(
                selected = selectedFilter == type,
                onClick = { onFilterSelected(type) },
                label = { Text(stringResource(countdownTypeLabelRes(type))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = countdownTypeColor(type).copy(alpha = 0.2f),
                    selectedLabelColor = countdownTypeColor(type),
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(end = dimens.spacerSmall),
            )
        }
    }
}

@Composable
internal fun CountdownCard(
    countdown: Countdown,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    val daysLeft = computeDaysUntil(countdown.targetDate)
    val isCountUp = countdown.countingMode == CountingMode.COUNT_UP
    val displayDays = if (isCountUp) abs(daysLeft) else daysLeft
    val subtitle = when {
        isCountUp && daysLeft < 0 -> stringResource(Res.string.countdown_days_since)
        isCountUp -> stringResource(Res.string.countdown_days_until)
        daysLeft < 0 -> stringResource(Res.string.countdown_days_ago)
        daysLeft == 0 -> stringResource(Res.string.today)
        else -> stringResource(Res.string.countdown_days_left)
    }
    val typeColor = countdownTypeColor(countdown.countdownType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingLarge, vertical = dimens.paddingSmall),
        onClick = onClick,
        shape = RoundedCornerShape(dimens.cornerXLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.paddingXLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Type indicator
            Box(
                modifier = Modifier
                    .size(dimens.touchTargetLarge)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = countdownTypeInitial(countdown.countdownType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                )
            }

            Spacer(Modifier.width(dimens.spacerXXLarge))

            // Title
            Text(
                text = countdown.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(dimens.spacerXLarge))

            // Day count
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (daysLeft == 0) "0" else displayDays.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (daysLeft < 0 && !isCountUp) MaterialTheme.colorScheme.onSurfaceVariant else PrimaryBlue,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Previews ------------------------------------------------------------------

internal val previewCountdowns = listOf(
    Countdown(
        id = "preview-1",
        title = "Christmas",
        targetDate = 1766620800000L,
        countdownType = CountdownType.HOLIDAY,
    ),
    Countdown(
        id = "preview-2",
        title = "Mom's Birthday",
        targetDate = 1773619200000L,
        countdownType = CountdownType.BIRTHDAY,
    ),
    Countdown(
        id = "preview-3",
        title = "Wedding Anniversary",
        targetDate = 1758240000000L,
        countdownType = CountdownType.ANNIVERSARY,
    ),
    Countdown(
        id = "preview-4",
        title = "Project Launch",
        targetDate = 1775088000000L,
        countdownType = CountdownType.COUNTDOWN,
        countingMode = CountingMode.COUNTDOWN,
    ),
)


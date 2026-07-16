package com.udnahc.opentasks.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udnahc.opentasks.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.widget_click_go_to_list
import opentasks.composeapp.generated.resources.widget_click_open_task
import opentasks.composeapp.generated.resources.widget_filter_all
import opentasks.composeapp.generated.resources.widget_filter_next_7_days
import opentasks.composeapp.generated.resources.widget_filter_today
import opentasks.composeapp.generated.resources.widget_filter_tomorrow
import opentasks.composeapp.generated.resources.widget_font_size_large
import opentasks.composeapp.generated.resources.widget_font_size_normal
import opentasks.composeapp.generated.resources.widget_font_size_small
import opentasks.composeapp.generated.resources.widget_group_by_date
import opentasks.composeapp.generated.resources.widget_group_by_priority
import opentasks.composeapp.generated.resources.widget_opacity
import opentasks.composeapp.generated.resources.widget_setting_font_size
import opentasks.composeapp.generated.resources.widget_setting_group_by
import opentasks.composeapp.generated.resources.widget_setting_hide_due_date
import opentasks.composeapp.generated.resources.widget_setting_hide_due_date_description
import opentasks.composeapp.generated.resources.widget_setting_list_tag
import opentasks.composeapp.generated.resources.widget_setting_on_task_click
import opentasks.composeapp.generated.resources.widget_setting_sort_by
import opentasks.composeapp.generated.resources.widget_setting_theme
import opentasks.composeapp.generated.resources.widget_settings_section_appearance
import opentasks.composeapp.generated.resources.widget_settings_section_behavior
import opentasks.composeapp.generated.resources.widget_settings_section_content
import opentasks.composeapp.generated.resources.widget_settings_title
import opentasks.composeapp.generated.resources.widget_sort_by_date
import opentasks.composeapp.generated.resources.widget_sort_by_name
import opentasks.composeapp.generated.resources.widget_sort_by_priority
import opentasks.composeapp.generated.resources.widget_theme_dark
import opentasks.composeapp.generated.resources.widget_theme_light
import opentasks.composeapp.generated.resources.widget_theme_system
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsContent(
    initialPreferences: WidgetPreferences,
    onSave: (WidgetPreferences) -> Unit,
    onCancel: () -> Unit,
) {
    var theme by remember { mutableStateOf(initialPreferences.theme) }
    var fontSize by remember { mutableStateOf(initialPreferences.fontSize) }
    var opacity by remember { mutableFloatStateOf(initialPreferences.opacity) }
    var filterType by remember { mutableStateOf(initialPreferences.filterType) }
    var filterCategoryId by remember { mutableStateOf(initialPreferences.filterCategoryId) }
    var groupBy by remember { mutableStateOf(initialPreferences.groupBy) }
    var sortBy by remember { mutableStateOf(initialPreferences.sortBy) }
    var hideDueDate by remember { mutableStateOf(initialPreferences.hideDueDate) }
    var onClickAction by remember { mutableStateOf(initialPreferences.onClickAction) }

    var categories by remember { mutableStateOf(emptyList<Category>()) }

    LaunchedEffect(Unit) {
        val loadedCategories = withContext(Dispatchers.IO) {
            WidgetDataProvider().getCategories()
        }
        categories = loadedCategories
        if (filterType == WidgetFilterType.CATEGORY &&
            loadedCategories.none { it.id == filterCategoryId }
        ) {
            filterType = WidgetFilterType.ALL
            filterCategoryId = null
        }
    }

    val accentColor = Color(0xFF4D9EFF)
    val surfaceColor = Color(0xFF1E1E1E)
    val cardColor = Color(0xFF2A2A2A)

    Scaffold(
        containerColor = surfaceColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.widget_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Text(
                            text = "\u2715",
                            color = Color.White,
                            fontSize = 20.sp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(
                            WidgetPreferences(
                                widgetId = initialPreferences.widgetId,
                                theme = theme,
                                fontSize = fontSize,
                                opacity = opacity,
                                filterType = filterType,
                                filterCategoryId = filterCategoryId,
                                groupBy = groupBy,
                                sortBy = sortBy,
                                hideDueDate = hideDueDate,
                                onClickAction = onClickAction,
                            )
                        )
                    }) {
                        Text(
                            text = "\u2713",
                            color = accentColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = surfaceColor,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Widget Preview
            WidgetPreviewSection(
                theme = theme,
                fontSize = fontSize,
                opacity = opacity,
                filterType = filterType,
                filterCategoryId = filterCategoryId,
                hideDueDate = hideDueDate,
                categories = categories,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance Section
            SectionHeader(title = stringResource(Res.string.widget_settings_section_appearance))
            SettingsCard(cardColor = cardColor) {
                DropdownSettingRow(
                    label = stringResource(Res.string.widget_setting_theme),
                    currentValue = theme.displayName(),
                    options = WidgetTheme.entries.map { it.name to it.displayName() },
                    onSelect = { theme = WidgetTheme.valueOf(it) },
                    accentColor = accentColor,
                )
                SettingsDivider()
                DropdownSettingRow(
                    label = stringResource(Res.string.widget_setting_font_size),
                    currentValue = fontSize.displayName(),
                    options = WidgetFontSize.entries.map { it.name to it.displayName() },
                    onSelect = { fontSize = WidgetFontSize.valueOf(it) },
                    accentColor = accentColor,
                )
                SettingsDivider()
                OpacityRow(
                    opacity = opacity,
                    onOpacityChange = { opacity = it },
                    accentColor = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content Section
            SectionHeader(title = stringResource(Res.string.widget_settings_section_content))
            SettingsCard(cardColor = cardColor) {
                FilterRow(
                    filterType = filterType,
                    filterCategoryId = filterCategoryId,
                    categories = categories,
                    onFilterSelected = { type, catId ->
                        filterType = type
                        filterCategoryId = catId
                    },
                    accentColor = accentColor,
                )
                SettingsDivider()
                DropdownSettingRow(
                    label = stringResource(Res.string.widget_setting_group_by),
                    currentValue = groupBy.displayName(),
                    options = WidgetGroupBy.entries.map { it.name to it.displayName() },
                    onSelect = { groupBy = WidgetGroupBy.valueOf(it) },
                    accentColor = accentColor,
                )
                SettingsDivider()
                DropdownSettingRow(
                    label = stringResource(Res.string.widget_setting_sort_by),
                    currentValue = sortBy.displayName(),
                    options = WidgetSortBy.entries.map { it.name to it.displayName() },
                    onSelect = { sortBy = WidgetSortBy.valueOf(it) },
                    accentColor = accentColor,
                )
                SettingsDivider()
                SwitchSettingRow(
                    label = stringResource(Res.string.widget_setting_hide_due_date),
                    description = stringResource(Res.string.widget_setting_hide_due_date_description),
                    checked = hideDueDate,
                    onCheckedChange = { hideDueDate = it },
                    accentColor = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Behavior Section
            SectionHeader(title = stringResource(Res.string.widget_settings_section_behavior))
            SettingsCard(cardColor = cardColor) {
                DropdownSettingRow(
                    label = stringResource(Res.string.widget_setting_on_task_click),
                    currentValue = onClickAction.displayName(),
                    options = WidgetClickAction.entries.map { it.name to it.displayName() },
                    onSelect = { onClickAction = WidgetClickAction.valueOf(it) },
                    accentColor = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -- Preview Section --

@Composable
private fun WidgetPreviewSection(
    theme: WidgetTheme,
    fontSize: WidgetFontSize,
    opacity: Float,
    filterType: WidgetFilterType,
    filterCategoryId: String?,
    hideDueDate: Boolean,
    categories: List<Category>,
) {
    val isDark = theme == WidgetTheme.DARK || theme == WidgetTheme.SYSTEM
    val bgAlpha = (opacity * 255).toInt()
    val bgColor = if (isDark) {
        Color(android.graphics.Color.argb(bgAlpha, 30, 30, 30))
    } else {
        Color(android.graphics.Color.argb(bgAlpha, 245, 245, 245))
    }
    val textColor = if (isDark) Color.White else Color.Black
    val subtleColor = if (isDark) Color.Gray else Color.DarkGray
    val dateColor = Color(0xFFC83C3C)

    val filterLabel = when (filterType) {
        WidgetFilterType.ALL -> stringResource(Res.string.widget_filter_all)
        WidgetFilterType.TODAY -> stringResource(Res.string.widget_filter_today)
        WidgetFilterType.TOMORROW -> stringResource(Res.string.widget_filter_tomorrow)
        WidgetFilterType.NEXT_7_DAYS -> stringResource(Res.string.widget_filter_next_7_days)
        WidgetFilterType.CATEGORY -> {
            categories.find { it.id == filterCategoryId }?.name
                ?: stringResource(Res.string.widget_filter_all)
        }
    }

    val textSizeSp = when (fontSize) {
        WidgetFontSize.SMALL -> 12.sp
        WidgetFontSize.NORMAL -> 14.sp
        WidgetFontSize.LARGE -> 16.sp
    }

    val sampleTasks = listOf(
        WidgetTask("1", "Review project proposal", "Today", isOverdue = false),
        WidgetTask("2", "Prepare meeting agenda", "Tomorrow", isOverdue = false),
        WidgetTask("3", "Update documentation", "Mar 28", isOverdue = false),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = filterLabel,
                color = textColor,
                fontSize = textSizeSp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+",
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Sample task rows
        sampleTasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title,
                    color = textColor,
                    fontSize = textSizeSp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!hideDueDate && task.dateLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.dateLabel,
                        color = dateColor,
                        fontSize = textSizeSp,
                    )
                }
            }
        }
    }
}

// -- Setting Card & Rows --

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        letterSpacing = 1.sp,
    )
}

@Composable
private fun SettingsCard(
    cardColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF3A3A3A)),
    )
}

@Composable
private fun DropdownSettingRow(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    accentColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
            )
            Text(
                text = currentValue,
                color = accentColor,
                fontSize = 15.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OpacityRow(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    accentColor: Color,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.widget_opacity),
                color = Color.White,
                fontSize = 15.sp,
            )
            Text(
                text = "${(opacity * 100).toInt()}%",
                color = accentColor,
                fontSize = 15.sp,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

@Composable
private fun FilterRow(
    filterType: WidgetFilterType,
    filterCategoryId: String?,
    categories: List<Category>,
    onFilterSelected: (WidgetFilterType, String?) -> Unit,
    accentColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = when (filterType) {
        WidgetFilterType.ALL -> stringResource(Res.string.widget_filter_all)
        WidgetFilterType.TODAY -> stringResource(Res.string.widget_filter_today)
        WidgetFilterType.TOMORROW -> stringResource(Res.string.widget_filter_tomorrow)
        WidgetFilterType.NEXT_7_DAYS -> stringResource(Res.string.widget_filter_next_7_days)
        WidgetFilterType.CATEGORY -> {
            categories.find { it.id == filterCategoryId }?.name
                ?: stringResource(Res.string.widget_filter_all)
        }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.widget_setting_list_tag),
                color = Color.White,
                fontSize = 15.sp,
            )
            Text(
                text = currentLabel,
                color = accentColor,
                fontSize = 15.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.widget_filter_all)) },
                onClick = {
                    onFilterSelected(WidgetFilterType.ALL, null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.widget_filter_today)) },
                onClick = {
                    onFilterSelected(WidgetFilterType.TODAY, null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.widget_filter_tomorrow)) },
                onClick = {
                    onFilterSelected(WidgetFilterType.TOMORROW, null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.widget_filter_next_7_days)) },
                onClick = {
                    onFilterSelected(WidgetFilterType.NEXT_7_DAYS, null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onFilterSelected(WidgetFilterType.CATEGORY, category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
            )
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

// -- Display name extensions --

@Composable
private fun WidgetTheme.displayName(): String = when (this) {
    WidgetTheme.DARK -> stringResource(Res.string.widget_theme_dark)
    WidgetTheme.LIGHT -> stringResource(Res.string.widget_theme_light)
    WidgetTheme.SYSTEM -> stringResource(Res.string.widget_theme_system)
}

@Composable
private fun WidgetFontSize.displayName(): String = when (this) {
    WidgetFontSize.SMALL -> stringResource(Res.string.widget_font_size_small)
    WidgetFontSize.NORMAL -> stringResource(Res.string.widget_font_size_normal)
    WidgetFontSize.LARGE -> stringResource(Res.string.widget_font_size_large)
}

@Composable
private fun WidgetGroupBy.displayName(): String = when (this) {
    WidgetGroupBy.DATE -> stringResource(Res.string.widget_group_by_date)
    WidgetGroupBy.PRIORITY -> stringResource(Res.string.widget_group_by_priority)
}

@Composable
private fun WidgetSortBy.displayName(): String = when (this) {
    WidgetSortBy.DATE -> stringResource(Res.string.widget_sort_by_date)
    WidgetSortBy.PRIORITY -> stringResource(Res.string.widget_sort_by_priority)
    WidgetSortBy.NAME -> stringResource(Res.string.widget_sort_by_name)
}

@Composable
private fun WidgetClickAction.displayName(): String = when (this) {
    WidgetClickAction.OPEN_TASK -> stringResource(Res.string.widget_click_open_task)
    WidgetClickAction.GO_TO_LIST -> stringResource(Res.string.widget_click_go_to_list)
}

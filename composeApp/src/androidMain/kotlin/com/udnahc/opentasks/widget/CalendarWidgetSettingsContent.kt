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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_widget_title
import opentasks.composeapp.generated.resources.widget_font_size_large
import opentasks.composeapp.generated.resources.widget_font_size_normal
import opentasks.composeapp.generated.resources.widget_font_size_small
import opentasks.composeapp.generated.resources.widget_opacity
import opentasks.composeapp.generated.resources.widget_setting_font_size
import opentasks.composeapp.generated.resources.widget_setting_theme
import opentasks.composeapp.generated.resources.widget_settings_section_appearance
import opentasks.composeapp.generated.resources.widget_theme_dark
import opentasks.composeapp.generated.resources.widget_theme_light
import opentasks.composeapp.generated.resources.widget_theme_system
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarWidgetSettingsContent(
    initialPreferences: CalendarWidgetPreferences,
    onSave: (CalendarWidgetPreferences) -> Unit,
    onCancel: () -> Unit,
    title: String? = null,
    previewContent: (@Composable (WidgetTheme, WidgetFontSize, Float) -> Unit)? = null,
) {
    var theme by remember { mutableStateOf(initialPreferences.theme) }
    var fontSize by remember { mutableStateOf(initialPreferences.fontSize) }
    var opacity by remember { mutableFloatStateOf(initialPreferences.opacity) }

    val accentColor = Color(0xFF4D9EFF)
    val surfaceColor = Color(0xFF1E1E1E)
    val cardColor = Color(0xFF2A2A2A)
    val screenTitle = title ?: stringResource(Res.string.calendar_widget_title)

    Scaffold(
        containerColor = surfaceColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = screenTitle,
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
                            CalendarWidgetPreferences(
                                widgetId = initialPreferences.widgetId,
                                theme = theme,
                                fontSize = fontSize,
                                opacity = opacity,
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
            if (previewContent != null) {
                previewContent(theme, fontSize, opacity)
            } else {
                CalendarPreviewSection(theme = theme, fontSize = fontSize, opacity = opacity)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance Section
            Text(
                text = stringResource(Res.string.widget_settings_section_appearance).uppercase(),
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                letterSpacing = 1.sp,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CalendarDropdownRow(
                        label = stringResource(Res.string.widget_setting_theme),
                        currentValue = theme.calDisplayName(),
                        options = WidgetTheme.entries.map { it.name to it.calDisplayName() },
                        onSelect = { theme = WidgetTheme.valueOf(it) },
                        accentColor = accentColor,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFF3A3A3A)),
                    )
                    CalendarDropdownRow(
                        label = stringResource(Res.string.widget_setting_font_size),
                        currentValue = fontSize.calDisplayName(),
                        options = WidgetFontSize.entries.map { it.name to it.calDisplayName() },
                        onSelect = { fontSize = WidgetFontSize.valueOf(it) },
                        accentColor = accentColor,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFF3A3A3A)),
                    )
                    CalendarOpacityRow(
                        opacity = opacity,
                        onOpacityChange = { opacity = it },
                        accentColor = accentColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -- Preview Section --

@Composable
private fun CalendarPreviewSection(
    theme: WidgetTheme,
    fontSize: WidgetFontSize,
    opacity: Float,
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
    val accentColor = Color(0xFF4D9EFF)

    val titleSp = when (fontSize) {
        WidgetFontSize.SMALL -> 11.sp
        WidgetFontSize.NORMAL -> 13.sp
        WidgetFontSize.LARGE -> 15.sp
    }
    val daySp = when (fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 11.sp
        WidgetFontSize.LARGE -> 13.sp
    }
    val headerSp = when (fontSize) {
        WidgetFontSize.SMALL -> 9.sp
        WidgetFontSize.NORMAL -> 10.sp
        WidgetFontSize.LARGE -> 12.sp
    }

    val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
    // Preview: March 2026 (starts on Sunday, 31 days)
    val previewDays = listOf(
        listOf(1, 2, 3, 4, 5, 6, 7),
        listOf(8, 9, 10, 11, 12, 13, 14),
        listOf(15, 16, 17, 18, 19, 20, 21),
        listOf(22, 23, 24, 25, 26, 27, 28),
        listOf(29, 30, 31, 0, 0, 0, 0),
    )
    val todayDay = 26
    val daysWithTasks = setOf(5, 12, 18, 26, 30)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "\u25C2", color = textColor, fontSize = 16.sp)
            Text(
                text = "March 2026",
                color = textColor,
                fontSize = titleSp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(text = "\u25B8", color = textColor, fontSize = 16.sp)
            Text(
                text = " \u22EE",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach { header ->
                Text(
                    text = header,
                    color = subtleColor,
                    fontSize = headerSp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Week rows
        previewDays.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                week.forEach { day ->
                    if (day == 0) {
                        Text(
                            text = "",
                            fontSize = daySp,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (day == todayDay) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (day in daysWithTasks) "$day\u2022" else "$day",
                                    color = Color.White,
                                    fontSize = daySp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (day in daysWithTasks) "$day\u2022" else "$day",
                            color = if (day in daysWithTasks) accentColor else textColor,
                            fontSize = daySp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// -- Settings rows --

@Composable
private fun CalendarDropdownRow(
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
            Text(text = label, color = Color.White, fontSize = 15.sp)
            Text(text = currentValue, color = accentColor, fontSize = 15.sp)
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
private fun CalendarOpacityRow(
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
            Text(text = stringResource(Res.string.widget_opacity), color = Color.White, fontSize = 15.sp)
            Text(text = "${(opacity * 100).toInt()}%", color = accentColor, fontSize = 15.sp)
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

// -- Display name extensions --

@Composable
private fun WidgetTheme.calDisplayName(): String = when (this) {
    WidgetTheme.DARK -> stringResource(Res.string.widget_theme_dark)
    WidgetTheme.LIGHT -> stringResource(Res.string.widget_theme_light)
    WidgetTheme.SYSTEM -> stringResource(Res.string.widget_theme_system)
}

@Composable
private fun WidgetFontSize.calDisplayName(): String = when (this) {
    WidgetFontSize.SMALL -> stringResource(Res.string.widget_font_size_small)
    WidgetFontSize.NORMAL -> stringResource(Res.string.widget_font_size_normal)
    WidgetFontSize.LARGE -> stringResource(Res.string.widget_font_size_large)
}

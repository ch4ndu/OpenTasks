package com.udnahc.opentasks.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeekPreviewSection(
    theme: WidgetTheme,
    fontSize: WidgetFontSize,
) {
    val isDark = theme == WidgetTheme.DARK ||
            (theme == WidgetTheme.SYSTEM && isSystemInDarkTheme())
    val bgColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val textColor = if (isDark) Color.White else Color.Black
    val subtleColor = if (isDark) Color.Gray else Color.DarkGray
    val accentColor = Color(0xFF4D9EFF)
    val dividerColor = Color(0x80888888)
    val taskBgColor = Color(0x4DFFB000) // medium priority bg
    val taskTextColor = Color(0xFFFFB000)

    val titleSp = when (fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 12.sp
        WidgetFontSize.LARGE -> 14.sp
    }
    val daySp = when (fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 11.sp
        WidgetFontSize.LARGE -> 13.sp
    }
    val headerSp = when (fontSize) {
        WidgetFontSize.SMALL -> 6.sp
        WidgetFontSize.NORMAL -> 7.sp
        WidgetFontSize.LARGE -> 9.sp
    }
    val taskSp = when (fontSize) {
        WidgetFontSize.SMALL -> 7.sp
        WidgetFontSize.NORMAL -> 8.sp
        WidgetFontSize.LARGE -> 10.sp
    }

    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val dayNumbers = listOf(23, 24, 25, 26, 27, 28, 29)
    val todayIndex = 4 // Thursday the 27th
    val daysWithTasks = setOf(1, 3, 4) // Mon, Wed, Thu have tasks
    val taskNames = mapOf(1 to "Meeting", 3 to "Review", 4 to "Deploy")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(4.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(40.dp))
            Text(
                text = "\u25C2",
                color = textColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = "Mar 23 - 29",
                color = textColor,
                fontSize = titleSp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "\u25B8",
                color = textColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.width(40.dp))
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Day columns
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEachIndexed { index, header ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(dividerColor),
                    )
                }

                val isToday = index == todayIndex
                val hasTask = index in daysWithTasks

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isToday) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor)
                                .padding(horizontal = 1.dp, vertical = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = header,
                                color = Color.White,
                                fontSize = headerSp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "${dayNumbers[index]}",
                                color = Color.White,
                                fontSize = daySp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            text = header,
                            color = subtleColor,
                            fontSize = headerSp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${dayNumbers[index]}",
                            color = textColor,
                            fontSize = daySp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (hasTask) {
                        Text(
                            text = taskNames[index] ?: "",
                            color = taskTextColor,
                            fontSize = taskSp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(taskBgColor)
                                .padding(horizontal = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

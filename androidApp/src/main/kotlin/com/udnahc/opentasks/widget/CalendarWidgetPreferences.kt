package com.udnahc.opentasks.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

data class CalendarWidgetPreferences(
    val widgetId: Int,
    val theme: WidgetTheme = WidgetTheme.DARK,
    val fontSize: WidgetFontSize = WidgetFontSize.NORMAL,
) {
    companion object {
        private fun prefsName(widgetId: Int) = "calendar_widget_$widgetId"

        fun load(context: Context, widgetId: Int): CalendarWidgetPreferences {
            val sp = context.getSharedPreferences(prefsName(widgetId), Context.MODE_PRIVATE)
            return CalendarWidgetPreferences(
                widgetId = widgetId,
                theme = WidgetTheme.entries.firstOrNull {
                    it.name == sp.getString("theme", WidgetTheme.DARK.name)
                } ?: WidgetTheme.DARK,
                fontSize = WidgetFontSize.entries.firstOrNull {
                    it.name == sp.getString("fontSize", WidgetFontSize.NORMAL.name)
                } ?: WidgetFontSize.NORMAL,
            )
        }

        // The widget is refreshed immediately after saving, so asynchronous apply() could expose stale preferences.
        @SuppressLint("ApplySharedPref")
        fun save(context: Context, prefs: CalendarWidgetPreferences) {
            context.getSharedPreferences(prefsName(prefs.widgetId), Context.MODE_PRIVATE).edit(commit = true) {
                putString("theme", prefs.theme.name)
                putString("fontSize", prefs.fontSize.name)
            }
        }

        fun delete(context: Context, widgetId: Int) {
            context.deleteSharedPreferences(prefsName(widgetId))
        }
    }
}

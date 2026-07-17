package com.udnahc.opentasks.widget

import android.content.Context

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
                theme = WidgetTheme.valueOf(
                    sp.getString("theme", WidgetTheme.DARK.name) ?: WidgetTheme.DARK.name
                ),
                fontSize = WidgetFontSize.valueOf(
                    sp.getString("fontSize", WidgetFontSize.NORMAL.name)
                        ?: WidgetFontSize.NORMAL.name
                ),
            )
        }

        fun save(context: Context, prefs: CalendarWidgetPreferences) {
            context.getSharedPreferences(prefsName(prefs.widgetId), Context.MODE_PRIVATE).edit()
                .putString("theme", prefs.theme.name)
                .putString("fontSize", prefs.fontSize.name)
                .commit()
        }

        fun delete(context: Context, widgetId: Int) {
            context.deleteSharedPreferences(prefsName(widgetId))
        }
    }
}

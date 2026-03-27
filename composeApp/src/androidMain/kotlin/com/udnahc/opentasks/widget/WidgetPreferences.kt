package com.udnahc.opentasks.widget

import android.content.Context

enum class WidgetTheme { DARK, LIGHT, SYSTEM }
enum class WidgetFontSize { SMALL, NORMAL, LARGE }
enum class WidgetFilterType { ALL, TODAY, TOMORROW, NEXT_7_DAYS, CATEGORY }
enum class WidgetGroupBy { DATE, PRIORITY }
enum class WidgetSortBy { DATE, PRIORITY, NAME }
enum class WidgetClickAction { OPEN_TASK, GO_TO_LIST }

data class WidgetPreferences(
    val widgetId: Int,
    val theme: WidgetTheme = WidgetTheme.DARK,
    val fontSize: WidgetFontSize = WidgetFontSize.NORMAL,
    val opacity: Float = 0.9f,
    val filterType: WidgetFilterType = WidgetFilterType.ALL,
    val filterCategoryId: String? = null,
    val groupBy: WidgetGroupBy = WidgetGroupBy.DATE,
    val sortBy: WidgetSortBy = WidgetSortBy.DATE,
    val hideDueDate: Boolean = false,
    val onClickAction: WidgetClickAction = WidgetClickAction.OPEN_TASK,
) {
    companion object {
        private fun prefsName(widgetId: Int) = "widget_$widgetId"

        fun load(context: Context, widgetId: Int): WidgetPreferences {
            val sp = context.getSharedPreferences(prefsName(widgetId), Context.MODE_PRIVATE)
            return WidgetPreferences(
                widgetId = widgetId,
                theme = WidgetTheme.valueOf(
                    sp.getString("theme", WidgetTheme.DARK.name) ?: WidgetTheme.DARK.name
                ),
                fontSize = WidgetFontSize.valueOf(
                    sp.getString("fontSize", WidgetFontSize.NORMAL.name)
                        ?: WidgetFontSize.NORMAL.name
                ),
                opacity = sp.getFloat("opacity", 0.9f),
                filterType = WidgetFilterType.valueOf(
                    sp.getString("filterType", WidgetFilterType.ALL.name)
                        ?: WidgetFilterType.ALL.name
                ),
                filterCategoryId = sp.getString("filterCategoryId", null),
                groupBy = WidgetGroupBy.valueOf(
                    sp.getString("groupBy", WidgetGroupBy.DATE.name) ?: WidgetGroupBy.DATE.name
                ),
                sortBy = WidgetSortBy.valueOf(
                    sp.getString("sortBy", WidgetSortBy.DATE.name) ?: WidgetSortBy.DATE.name
                ),
                hideDueDate = sp.getBoolean("hideDueDate", false),
                onClickAction = WidgetClickAction.valueOf(
                    sp.getString("onClickAction", WidgetClickAction.OPEN_TASK.name)
                        ?: WidgetClickAction.OPEN_TASK.name
                ),
            )
        }

        fun save(context: Context, prefs: WidgetPreferences) {
            context.getSharedPreferences(prefsName(prefs.widgetId), Context.MODE_PRIVATE).edit()
                .putString("theme", prefs.theme.name)
                .putString("fontSize", prefs.fontSize.name)
                .putFloat("opacity", prefs.opacity)
                .putString("filterType", prefs.filterType.name)
                .putString("filterCategoryId", prefs.filterCategoryId)
                .putString("groupBy", prefs.groupBy.name)
                .putString("sortBy", prefs.sortBy.name)
                .putBoolean("hideDueDate", prefs.hideDueDate)
                .putString("onClickAction", prefs.onClickAction.name)
                .commit()
        }

        fun delete(context: Context, widgetId: Int) {
            context.deleteSharedPreferences(prefsName(widgetId))
        }
    }
}

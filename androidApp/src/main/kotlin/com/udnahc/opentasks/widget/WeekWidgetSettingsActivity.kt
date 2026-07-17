package com.udnahc.opentasks.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.week_widget_title
import org.jetbrains.compose.resources.stringResource
import org.lighthousegames.logging.logging

private val log = logging("WeekWidgetSettingsActivity")

class WeekWidgetSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initialPrefs = CalendarWidgetPreferences.load(this, appWidgetId)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CalendarWidgetSettingsContent(
                    initialPreferences = initialPrefs,
                    onSave = { prefs -> saveAndFinish(prefs, appWidgetId) },
                    onCancel = { finish() },
                    title = stringResource(Res.string.week_widget_title),
                    previewContent = { theme, fontSize ->
                        WeekPreviewSection(theme, fontSize)
                    },
                )
            }
        }
    }

    private fun saveAndFinish(prefs: CalendarWidgetPreferences, appWidgetId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            CalendarWidgetPreferences.save(this@WeekWidgetSettingsActivity, prefs)
            WeekWidget.refreshWidget(this@WeekWidgetSettingsActivity, appWidgetId)
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
        }
    }
}

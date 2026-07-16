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
import org.lighthousegames.logging.logging

private val log = logging("CalendarWidgetSettingsActivity")

class CalendarWidgetSettingsActivity : ComponentActivity() {

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
                )
            }
        }
    }

    private fun saveAndFinish(prefs: CalendarWidgetPreferences, appWidgetId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            CalendarWidgetPreferences.save(this@CalendarWidgetSettingsActivity, prefs)
            CalendarWidget.refreshWidget(this@CalendarWidgetSettingsActivity, appWidgetId)
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
        }
    }
}

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
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val log = logging("WidgetSettingsActivity")

class WidgetSettingsActivity : ComponentActivity(), KoinComponent {

    private val widgetAccountGate: WidgetAccountGate by inject()

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

        // Set RESULT_CANCELED initially so if user backs out, the widget won't be added
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val initialPrefs = widgetAccountGate.withAuthenticatedBoundary {
                WidgetPreferences.load(this@WidgetSettingsActivity, appWidgetId)
            }
            withContext(Dispatchers.Main) {
                if (initialPrefs == null) {
                    finish()
                    return@withContext
                }
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        WidgetSettingsContent(
                            initialPreferences = initialPrefs,
                            onSave = { prefs -> saveAndFinish(prefs, appWidgetId) },
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun saveAndFinish(prefs: WidgetPreferences, appWidgetId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                WidgetPreferences.save(this@WidgetSettingsActivity, prefs)
                TaskWidget.refreshWidgetWithinBoundary(this@WidgetSettingsActivity, appWidgetId, boundary)
            }
            withContext(Dispatchers.Main) {
                if (saved != null) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                }
                finish()
            }
        }
    }
}

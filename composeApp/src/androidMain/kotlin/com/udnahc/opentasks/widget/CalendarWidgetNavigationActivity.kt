package com.udnahc.opentasks.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("CalendarWidgetNav")

class CalendarWidgetNavigationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.getIntExtra(
            "appWidgetId",
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val delta = intent.getIntExtra("delta", 0)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || delta == 0) {
            finish()
            return
        }

        log.d { "Navigating month: widget=$appWidgetId, delta=$delta" }

        lifecycleScope.launch(Dispatchers.IO) {
            CalendarWidget.navigateMonth(this@CalendarWidgetNavigationActivity, appWidgetId, delta)
            finish()
        }
    }
}

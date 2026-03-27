package com.udnahc.opentasks.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("WidgetMenu")

class WidgetMenuActivity : ComponentActivity(), KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable { finish() },
                contentAlignment = Alignment.TopEnd,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 48.dp, end = 16.dp)
                        .clickable { /* consume click, prevent dismiss */ },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2A2A2A),
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Refresh",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable {
                                    log.d { "Refresh tapped for widget $appWidgetId" }
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            triggerSyncAction()
                                            TaskWidget.refreshWidget(this@WidgetMenuActivity, appWidgetId)
                                            log.d { "Widget refreshed" }
                                        } catch (e: Exception) {
                                            log.e { "Refresh failed: ${e.message}" }
                                        }
                                        withContext(Dispatchers.Main) { finish() }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )

                        HorizontalDivider(color = Color(0xFF444444))

                        Text(
                            text = "Settings",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable {
                                    val settingsIntent = Intent(
                                        this@WidgetMenuActivity,
                                        WidgetSettingsActivity::class.java,
                                    ).apply {
                                        putExtra(
                                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                                            appWidgetId,
                                        )
                                    }
                                    startActivity(settingsIntent)
                                    finish()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

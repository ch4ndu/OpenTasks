package com.udnahc.opentasks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.sync.SyncWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var deepLinkTaskId by mutableStateOf("")
    private var widgetAction by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }

        deepLinkTaskId = intent?.getStringExtra(NotificationScheduler.EXTRA_TASK_ID).orEmpty()
        widgetAction = intent?.getStringExtra("widget_action").orEmpty()

        schedulePeriodicSync()

        setContent {
            App(
                sharedText = sharedText,
                deepLinkTaskId = deepLinkTaskId,
                widgetAction = widgetAction,
            )
        }
    }

    private fun schedulePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_and_schedule",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkTaskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID).orEmpty()
        widgetAction = intent.getStringExtra("widget_action").orEmpty()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

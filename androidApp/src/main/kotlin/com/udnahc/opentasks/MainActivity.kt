package com.udnahc.opentasks

import android.content.Intent
import android.net.Uri
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.sync.SyncWorker
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var deepLinkNotificationEvent by mutableStateOf<NotificationDeepLinkEvent?>(null)
    private var deepLinkCountdownId by mutableStateOf("")
    private var widgetNavigationEvent by mutableStateOf<WidgetNavigationEvent?>(null)
    private val widgetNavigationEventPublisher = WidgetNavigationEventPublisher()

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

        publishShareIntent(intent)
        handleDeepLinkIntent(intent)
        publishWidgetNavigationIntent(intent)

        schedulePeriodicSync()

        setContent {
            App(
                deepLinkNotificationEvent = deepLinkNotificationEvent,
                deepLinkCountdownId = deepLinkCountdownId,
                widgetNavigationEvent = widgetNavigationEvent,
            )
        }
    }

    private fun schedulePeriodicSync() {
        val syncRequest = periodicSyncRequest()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_and_schedule",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishShareIntent(intent)
        handleDeepLinkIntent(intent)
        publishWidgetNavigationIntent(intent)
    }

    private fun publishShareIntent(intent: Intent?) {
        val payload = intent?.toSharedTaskPayload() ?: return
        publishSharedTaskPayload(
            id = System.currentTimeMillis(),
            description = payload.description,
            url = payload.url,
            icsContent = payload.icsContent,
        )
    }

    private fun Intent.toSharedTaskPayload(): AndroidSharedTaskPayload? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val mimeType = type.orEmpty()
        val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val icsContent = when {
            text.isIcsContent() -> text
            mimeType.isCalendarMimeType() -> streamUris().mapNotNull { readTextFromUri(it) }
                .joinToString("\n")

            else -> ""
        }

        return if (icsContent.isNotBlank()) {
            AndroidSharedTaskPayload(icsContent = icsContent)
        } else {
            val description = text.ifBlank {
                getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
            }
            if (description.isBlank()) return null
            AndroidSharedTaskPayload(
                description = description,
                url = description.firstUrl().orEmpty(),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND_MULTIPLE -> getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        Intent.ACTION_SEND -> listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM))
        else -> emptyList()
    }

    private fun readTextFromUri(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { it.readText() }
        }
    }.getOrNull()

    private fun handleDeepLinkIntent(intent: Intent?) {
        val eventId = intent?.getStringExtra(NotificationScheduler.EXTRA_TASK_ID).orEmpty()
        if (eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
            deepLinkNotificationEvent = null
            deepLinkCountdownId = eventId.removePrefix(COUNTDOWN_ID_PREFIX)
        } else if (eventId.isNotBlank()) {
            deepLinkNotificationEvent = NotificationDeepLinkEvent(
                eventId = eventId,
                occurrenceDeadlineUtcMillis = intent.optionalLongExtra(
                    NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC
                ),
                notificationAtUtcMillis = intent.optionalLongExtra(
                    NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC
                ),
                semanticKey = intent?.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY),
            )
            deepLinkCountdownId = ""
        } else {
            deepLinkNotificationEvent = null
            deepLinkCountdownId = ""
        }
    }

    private fun publishWidgetNavigationIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        val navigationAction = when (action) {
            ACTION_CREATE_TASK -> WidgetNavigationAction.CREATE_TASK
            ACTION_VIEW_LIST -> WidgetNavigationAction.VIEW_LIST
            ACTION_VIEW_TASK -> WidgetNavigationAction.VIEW_TASK
            ACTION_VIEW_CALENDAR -> WidgetNavigationAction.VIEW_CALENDAR
            else -> return
        }
        val taskId = intent.getStringExtra(EXTRA_WIDGET_TASK_ID)?.takeIf { it.isNotBlank() }
        val calendarDate = if (navigationAction == WidgetNavigationAction.VIEW_CALENDAR) {
            WidgetCalendarDate(
                year = intent.getIntExtra(EXTRA_WIDGET_CALENDAR_YEAR, 0),
                month = intent.getIntExtra(EXTRA_WIDGET_CALENDAR_MONTH, 0),
                day = intent.getIntExtra(EXTRA_WIDGET_CALENDAR_DAY, 0),
            ).takeIf { it.isValid }
        } else {
            null
        }
        widgetNavigationEvent = widgetNavigationEventPublisher.publish(
            action = navigationAction,
            taskId = taskId,
            calendarDate = calendarDate,
        ) ?: return
    }

    private fun Intent?.optionalLongExtra(key: String): Long? =
        if (this != null && hasExtra(key)) getLongExtra(key, 0L).takeIf { it > 0L } else null
}

/** The worker dynamically skips network sync but still performs offline maintenance. */
internal fun periodicSyncRequest() =
    PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS).build()

const val EXTRA_WIDGET_ACTION = "widget_action"
const val EXTRA_WIDGET_TASK_ID = "task_id"
const val EXTRA_WIDGET_CALENDAR_YEAR = "calendar_year"
const val EXTRA_WIDGET_CALENDAR_MONTH = "calendar_month"
const val EXTRA_WIDGET_CALENDAR_DAY = "calendar_day"
const val ACTION_CREATE_TASK = "create_task"
const val ACTION_VIEW_LIST = "view_list"
const val ACTION_VIEW_TASK = "view_task"
const val ACTION_VIEW_CALENDAR = "view_calendar"


private data class AndroidSharedTaskPayload(
    val description: String = "",
    val url: String = "",
    val icsContent: String = "",
)

private fun String.isCalendarMimeType(): Boolean =
    equals("text/calendar", ignoreCase = true) ||
            equals("text/x-vcalendar", ignoreCase = true)

private fun String.isIcsContent(): Boolean =
    contains("BEGIN:VCALENDAR", ignoreCase = true) &&
            contains("BEGIN:VEVENT", ignoreCase = true)

private fun String.firstUrl(): String? =
    Regex("""https?://\S+""").find(this)?.value?.trimEnd('.', ',', ')', ']')

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

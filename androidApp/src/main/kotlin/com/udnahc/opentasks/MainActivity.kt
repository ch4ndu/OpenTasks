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
import androidx.core.view.WindowCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.sync.SyncWorker
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("MainActivity")

class MainActivity : ComponentActivity(), KoinComponent {

    private var deepLinkNotificationEvent by mutableStateOf<NotificationDeepLinkEvent?>(null)
    private var widgetNavigationEvent by mutableStateOf<WidgetNavigationEvent?>(null)
    private val widgetNavigationEventPublisher = WidgetNavigationEventPublisher()
    private val widgetAccountGate: WidgetAccountGate by inject()

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

        setContent {
            App(
                deepLinkNotificationEvent = deepLinkNotificationEvent,
                widgetNavigationEvent = widgetNavigationEvent,
                onNotificationDeepLinkEventConsumed = { event ->
                    deepLinkNotificationEvent =
                        consumeNotificationDeepLinkEvent(deepLinkNotificationEvent, event)
                },
                onAccountBoundaryChanged = { binding ->
                    refreshWidgetsAndScheduleSync(binding)
                },
                onSystemBarIconAppearanceChanged = ::updateSystemBarIconAppearance,
            )
        }
    }

    private fun updateSystemBarIconAppearance(useDarkIcons: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }

    private suspend fun refreshWidgetsAndScheduleSync(binding: CacheBinding?) {
        if (binding == null) {
            WorkManager.getInstance(this).cancelUniqueWork("sync_and_schedule")
            TaskWidget.blankAllWidgets(this)
            CalendarWidget.blankAllWidgets(this)
            WeekWidget.blankAllWidgets(this)
            return
        }
        try {
            widgetAccountGate.withForegroundBoundary { currentBoundary ->
                if (!currentBoundary.matches(binding)) return@withForegroundBoundary

                TaskWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                CalendarWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                WeekWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                val workManager = WorkManager.getInstance(this)
                val syncRequest = periodicSyncRequest(binding)
                workManager.enqueueUniquePeriodicWork(
                    "sync_and_schedule",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    syncRequest,
                )
            }
        } catch (_: AccountBoundaryRejectedException) {
            log.w { "Widget refresh skipped because the foreground account boundary changed" }
        }
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
        deepLinkNotificationEvent = intent.toNotificationDeepLinkEvent()
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
            accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID),
            boundaryEpoch = intent.getLongExtra(EXTRA_BOUNDARY_EPOCH, 0L),
        ) ?: return
    }
}

internal fun Intent?.toNotificationDeepLinkEvent(): NotificationDeepLinkEvent? =
    createNotificationDeepLinkEvent(
        eventId = this?.getStringExtra(NotificationScheduler.EXTRA_TASK_ID),
        occurrenceDeadlineUtcMillis = optionalLongExtra(
            NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC,
        ),
        notificationAtUtcMillis = optionalLongExtra(
            NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC,
        ),
        semanticKey = this?.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY),
        accountId = this?.getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID),
        boundaryEpoch = this?.getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L) ?: 0L,
    )

internal fun createNotificationDeepLinkEvent(
    eventId: String?,
    occurrenceDeadlineUtcMillis: Long?,
    notificationAtUtcMillis: Long?,
    semanticKey: String?,
    accountId: String?,
    boundaryEpoch: Long,
): NotificationDeepLinkEvent? {
    val normalizedEventId = eventId?.takeIf { it.isNotBlank() } ?: return null
    return NotificationDeepLinkEvent(
        eventId = normalizedEventId,
        occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
        notificationAtUtcMillis = notificationAtUtcMillis,
        semanticKey = semanticKey,
        accountId = accountId,
        boundaryEpoch = boundaryEpoch,
    )
}

private fun Intent?.optionalLongExtra(key: String): Long? =
    if (this != null && hasExtra(key)) getLongExtra(key, 0L).takeIf { it > 0L } else null

/** The worker dynamically skips network sync but still performs offline maintenance. */
internal fun periodicSyncRequest(binding: CacheBinding) =
    PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
        .setInputData(
            workDataOf(
                SyncWorker.KEY_ACCOUNT_ID to binding.accountId,
                SyncWorker.KEY_BOUNDARY_EPOCH to binding.boundaryEpoch,
            )
        )
        .build()

const val EXTRA_WIDGET_ACTION = "widget_action"
const val EXTRA_WIDGET_TASK_ID = "task_id"
const val EXTRA_WIDGET_CALENDAR_YEAR = "calendar_year"
const val EXTRA_WIDGET_CALENDAR_MONTH = "calendar_month"
const val EXTRA_WIDGET_CALENDAR_DAY = "calendar_day"
const val EXTRA_ACCOUNT_ID = "account_id"
const val EXTRA_BOUNDARY_EPOCH = "boundary_epoch"
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

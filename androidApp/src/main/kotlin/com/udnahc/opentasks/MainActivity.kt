package com.udnahc.opentasks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
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
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.notification.ReminderCommand
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.notification.refreshNotificationWidgetsIndependently
import com.udnahc.opentasks.data.sync.SyncWorker
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.ExternalInputPolicy
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var shareParseGeneration = 0L
    private var shareParseJob: Job? = null
    private var nextSharePayloadId = System.currentTimeMillis()

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
                onTaskNotificationWidgetsRefresh = ::refreshTaskNotificationWidgets,
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

    private suspend fun refreshTaskNotificationWidgets(boundary: AccountBoundary) {
        try {
            widgetAccountGate.withForegroundBoundary(boundary) { currentBoundary ->
                refreshNotificationWidgetsIndependently(
                    refreshTaskWidget = {
                        TaskWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                    },
                    refreshCalendarWidget = {
                        CalendarWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                    },
                    refreshWeekWidget = {
                        WeekWidget.refreshAllWidgetsWithinBoundary(this, currentBoundary)
                    },
                )
            }
        } catch (_: AccountBoundaryRejectedException) {
            log.w { "Notification widget refresh skipped because the account boundary changed" }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishShareIntent(intent)
        handleDeepLinkIntent(intent)
        publishWidgetNavigationIntent(intent)
    }

    private fun publishShareIntent(intent: Intent?) {
        val incomingIntent = intent ?: return
        if (incomingIntent.action != Intent.ACTION_SEND &&
            incomingIntent.action != Intent.ACTION_SEND_MULTIPLE
        ) return
        val generation = ++shareParseGeneration
        val payloadId = ++nextSharePayloadId
        shareParseJob?.cancel()
        val unreadableShareRequest = AndroidShareRequest(
            mimeType = "",
            text = "",
            subject = "",
            streamUris = emptyList(),
            itemFailure = ExternalInputFailure.UNREADABLE,
        )
        val shareRequest = try {
            incomingIntent.toShareRequest()
        } catch (_: Exception) {
            unreadableShareRequest
        } ?: unreadableShareRequest

        val job = lifecycleScope.launch(
            context = Dispatchers.IO,
            start = CoroutineStart.LAZY,
        ) {
            val originatingJob = coroutineContext[Job]
            val result = try {
                shareRequest.parse()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AndroidShareParseResult.Rejected(ExternalInputFailure.UNREADABLE)
            }
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                if (shareParseGeneration != generation ||
                    shareParseJob !== originatingJob ||
                    originatingJob == null ||
                    !originatingJob.isActive
                ) {
                    return@withContext
                }
                when (result) {
                    is AndroidShareParseResult.Accepted -> publishSharedTaskPayload(
                        id = payloadId,
                        description = result.payload.description,
                        url = result.payload.url,
                        icsContent = result.payload.icsContent,
                        icsFileName = result.payload.icsFileName,
                    )
                    is AndroidShareParseResult.Rejected -> {
                        publishSharedTaskPayloadRejection(payloadId, result.reason)
                    }
                    AndroidShareParseResult.Ignored -> Unit
                }
            }
        }
        shareParseJob = job
        job.start()
    }

    @Suppress("DEPRECATION")
    private fun Intent.toShareRequest(): AndroidShareRequest? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val uris = when (action) {
            Intent.ACTION_SEND_MULTIPLE -> getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                .orEmpty()
                .mapNotNull { it as? Uri }
            Intent.ACTION_SEND -> listOfNotNull(getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM))
                .mapNotNull { it as? Uri }
            else -> emptyList()
        }
        val boundedUris = uris.take(ExternalInputPolicy.MAX_SHARE_ITEMS + 1)
        return AndroidShareRequest(
            mimeType = type.orEmpty(),
            text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),
            subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty(),
            streamUris = boundedUris,
            itemFailure = ExternalInputPolicy.validateShareItemCount(uris.size),
        )
    }

    private suspend fun AndroidShareRequest.parse(): AndroidShareParseResult {
        itemFailure?.let { return AndroidShareParseResult.Rejected(it) }

        if (text.isIcsContent()) {
            if (ExternalInputPolicy.utf8ByteCountUpTo(
                    text,
                    ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES,
                ) > ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES
            ) {
                return AndroidShareParseResult.Rejected(ExternalInputFailure.TOO_LARGE)
            }
            return AndroidSharedTaskPayload(
                icsContent = text,
            ).asParseResult()
        }

        if (mimeType.isCalendarMimeType()) {
            val parts = mutableListOf<String>()
            var remaining = ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES -
                ExternalInputPolicy.utf8ByteCountUpTo("shared.ics", ExternalInputPolicy.MAX_ICS_FILENAME_BYTES)
            for (uri in streamUris) {
                if (parts.isNotEmpty()) {
                    if (remaining == 0) return AndroidShareParseResult.Rejected(ExternalInputFailure.TOO_LARGE)
                    remaining--
                }
                when (val result = readTextFromUri(uri, remaining)) {
                    is AndroidTextReadResult.Success -> {
                        val byteCount = ExternalInputPolicy.utf8ByteCountUpTo(result.content, remaining)
                        if (byteCount > remaining) {
                            return AndroidShareParseResult.Rejected(ExternalInputFailure.TOO_LARGE)
                        }
                        remaining -= byteCount
                        parts += result.content
                    }
                    AndroidTextReadResult.TooLarge -> {
                        return AndroidShareParseResult.Rejected(ExternalInputFailure.TOO_LARGE)
                    }
                    AndroidTextReadResult.InvalidUtf8 -> {
                        return AndroidShareParseResult.Rejected(ExternalInputFailure.INVALID_UTF8)
                    }
                    AndroidTextReadResult.Unreadable -> {
                        return AndroidShareParseResult.Rejected(ExternalInputFailure.UNREADABLE)
                    }
                }
            }
            val icsContent = parts.joinToString("\n")
            if (icsContent.isNotBlank()) {
                return AndroidSharedTaskPayload(icsContent = icsContent).asParseResult()
            }
        }

        val description = text.ifBlank { subject }
        if (description.isBlank()) return AndroidShareParseResult.Ignored
        if (ExternalInputPolicy.utf8ByteCountUpTo(
                description,
                ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES,
            ) > ExternalInputPolicy.MAX_SHARE_PAYLOAD_BYTES
        ) {
            return AndroidShareParseResult.Rejected(ExternalInputFailure.TOO_LARGE)
        }
        return AndroidSharedTaskPayload(
            description = description,
            url = description.firstUrl().orEmpty(),
        ).asParseResult()
    }

    private suspend fun readTextFromUri(uri: Uri, maxBytes: Int): AndroidTextReadResult {
        val input = try {
            contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        } ?: return AndroidTextReadResult.Unreadable
        return try {
            when (val result = readBoundedUtf8(input, maxBytes)) {
                is BoundedText.Success -> AndroidTextReadResult.Success(result.content)
                BoundedText.TooLarge -> AndroidTextReadResult.TooLarge
                BoundedText.InvalidUtf8 -> AndroidTextReadResult.InvalidUtf8
                BoundedText.Unreadable -> AndroidTextReadResult.Unreadable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AndroidTextReadResult.Unreadable
        } finally {
            input.close()
        }
    }

    private suspend fun readBoundedUtf8(input: InputStream, maxBytes: Int): BoundedText {
        val bytes = ByteArrayOutputStream(minOf(maxBytes + 1, 8192))
        val buffer = ByteArray(minOf(maxBytes + 1, 8192))
        while (bytes.size() <= maxBytes) {
            currentCoroutineContext().ensureActive()
            val requested = minOf(buffer.size, maxBytes + 1 - bytes.size())
            val count = input.read(buffer, 0, requested)
            if (count < 0) break
            if (count == 0) continue
            bytes.write(buffer, 0, count)
        }
        currentCoroutineContext().ensureActive()
        val contentBytes = bytes.toByteArray()
        if (contentBytes.size > maxBytes) return BoundedText.TooLarge
        if (!ExternalInputPolicy.isStrictUtf8(contentBytes)) return BoundedText.InvalidUtf8
        return try {
            val content = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(contentBytes))
                .toString()
            BoundedText.Success(content)
        } catch (_: CharacterCodingException) {
            BoundedText.InvalidUtf8
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        intent.toNotificationDeepLinkEvent()?.let { event ->
            deepLinkNotificationEvent = event
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
            accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID),
            boundaryEpoch = intent.getLongExtra(EXTRA_BOUNDARY_EPOCH, 0L),
        ) ?: return
    }
}

internal fun Intent?.toNotificationDeepLinkEvent(): NotificationDeepLinkEvent? {
    val intent = this ?: return null
    return createAndroidNotificationDeepLinkEvent(
        uri = intent.data?.toAndroidReminderTapUri(),
        eventId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID),
        occurrenceDeadlineUtcMillis = intent.optionalLongExtra(
            NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC,
        ),
        notificationAtUtcMillis = intent.optionalLongExtra(
            NotificationScheduler.EXTRA_NOTIFICATION_AT_UTC,
        ),
        semanticKey = intent.getStringExtra(NotificationScheduler.EXTRA_SEMANTIC_KEY),
        accountId = intent.getStringExtra(NotificationScheduler.EXTRA_ACCOUNT_ID),
        boundaryEpoch = intent.getLongExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, 0L),
    )
}

internal data class AndroidReminderTapUri(
    val scheme: String?,
    val authority: String?,
    val encodedPath: String?,
    val encodedFragment: String?,
    val queryParameterNames: Set<String>,
    val keyValues: List<String>,
)

internal fun createAndroidNotificationDeepLinkEvent(
    uri: AndroidReminderTapUri?,
    eventId: String?,
    occurrenceDeadlineUtcMillis: Long?,
    notificationAtUtcMillis: Long?,
    semanticKey: String?,
    accountId: String?,
    boundaryEpoch: Long,
): NotificationDeepLinkEvent? {
    val command = androidReminderTapCommand(uri, semanticKey, eventId) ?: return null
    return createValidatedNotificationDeepLinkEvent(
        command = command,
        eventId = eventId,
        occurrenceDeadlineUtcMillis = occurrenceDeadlineUtcMillis,
        notificationAtUtcMillis = notificationAtUtcMillis,
        semanticKey = semanticKey,
        accountId = accountId,
        boundaryEpoch = boundaryEpoch,
    )
}

internal fun androidReminderTapCommand(
    uri: AndroidReminderTapUri?,
    semanticKey: String?,
    eventId: String?,
): ReminderCommand? {
    val tapUri = uri ?: return null
    if (tapUri.scheme != "opentasks" ||
        tapUri.authority != "reminder" ||
        tapUri.encodedFragment != null ||
        tapUri.queryParameterNames != setOf("key") ||
        tapUri.keyValues.size != 1 ||
        semanticKey.isNullOrBlank() ||
        tapUri.keyValues.single() != semanticKey ||
        eventId.isNullOrBlank()
    ) {
        return null
    }
    return when (tapUri.encodedPath) {
        "/${NotificationScheduler.ROLE_TAP}" ->
            if (eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
                ReminderCommand.COUNTDOWN_TAP
            } else {
                ReminderCommand.TASK_TAP
            }
        "/${NotificationScheduler.ROLE_ONGOING_TAP}" ->
            if (eventId.startsWith(COUNTDOWN_ID_PREFIX)) null else ReminderCommand.ONGOING_TAP
        else -> null
    }
}

private fun Uri.toAndroidReminderTapUri(): AndroidReminderTapUri? {
    if (!isHierarchical) return null
    return AndroidReminderTapUri(
        scheme = scheme,
        authority = authority,
        encodedPath = encodedPath,
        encodedFragment = encodedFragment,
        queryParameterNames = queryParameterNames,
        keyValues = getQueryParameters("key"),
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


private data class AndroidShareRequest(
    val mimeType: String,
    val text: String,
    val subject: String,
    val streamUris: List<Uri>,
    val itemFailure: ExternalInputFailure?,
)

private data class AndroidSharedTaskPayload(
    val description: String = "",
    val url: String = "",
    val icsContent: String = "",
    val icsFileName: String = "shared.ics",
)

private sealed interface AndroidShareParseResult {
    data class Accepted(val payload: AndroidSharedTaskPayload) : AndroidShareParseResult
    data class Rejected(val reason: ExternalInputFailure) : AndroidShareParseResult
    data object Ignored : AndroidShareParseResult
}

private sealed interface AndroidTextReadResult {
    data class Success(val content: String) : AndroidTextReadResult
    data object TooLarge : AndroidTextReadResult
    data object InvalidUtf8 : AndroidTextReadResult
    data object Unreadable : AndroidTextReadResult
}

private sealed interface BoundedText {
    data class Success(val content: String) : BoundedText
    data object TooLarge : BoundedText
    data object InvalidUtf8 : BoundedText
    data object Unreadable : BoundedText
}

private fun AndroidSharedTaskPayload.asParseResult(): AndroidShareParseResult =
    ExternalInputPolicy.validateSharePayload(
        description = description,
        url = url,
        icsContent = icsContent,
        icsFileName = icsFileName,
    )?.let(AndroidShareParseResult::Rejected)
        ?: AndroidShareParseResult.Accepted(this)

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

package com.udnahc.opentasks.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import com.udnahc.opentasks.data.auth.AccountMutationGate
import org.lighthousegames.logging.logging

private val log = logging("NotificationScheduler")

private const val MAIN_ACTIVITY_CLASS = "com.udnahc.opentasks.MainActivity"
private const val NOTIFICATION_RECEIVER_CLASS = "com.udnahc.opentasks.data.notification.NotificationReceiver"
private const val NOTIFICATION_ACTION_RECEIVER_CLASS =
    "com.udnahc.opentasks.data.notification.NotificationActionReceiver"

actual class NotificationScheduler(
    private val context: Context,
    private val mutationGate: AccountMutationGate,
    private val boundaryGuard: AccountBoundaryGuard,
) : ReminderScheduler {

    private val keyStore = AndroidReminderKeyStore(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.appString("notification_channel_task_reminders"),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.appString("notification_channel_task_reminders_description")
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.appString("notification_channel_all_day_tasks"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.appString("notification_channel_all_day_tasks_description")
        }
        manager.createNotificationChannel(ongoingChannel)
    }

    actual override suspend fun schedule(request: ReminderRequest) =
        withCurrentHeldReminderBoundary { boundary ->
            scheduleWithinHeldBoundary(request, boundary)
        }

    private suspend fun scheduleWithinHeldBoundary(
        request: ReminderRequest,
        boundary: AccountBoundary,
    ) {
        cleanupLegacyOnce(request.eventId)
        val allocation = keyStore.allocatePending(request.identity)
        cancelPlatform(allocation, removeFromStore = false)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            shouldUseExactAlarm(
            sdkInt = Build.VERSION.SDK_INT,
                canScheduleExactAlarms = alarmManager.canScheduleExactAlarms(),
            )
        }
        val intent = context.appComponentIntent(NOTIFICATION_RECEIVER_CLASS).apply {
            data = pendingIntentUri(request.identity.semanticKey, ROLE_ALARM)
            putExtra(EXTRA_TASK_ID, request.eventId)
            putExtra(EXTRA_TITLE, request.title)
            putExtra(EXTRA_BODY, request.body)
            putExtra(EXTRA_SEMANTIC_KEY, request.identity.semanticKey)
            putExtra(EXTRA_NOTIFICATION_ID, allocation.notificationId)
            putExtra(EXTRA_NOTIFICATION_AT_UTC, request.triggerAtUtcMillis)
            putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, request.occurrenceUtcMillis)
            putExtra(EXTRA_ALLOW_MARK_DONE, request.allowMarkDone)
            putExtra(EXTRA_RESCHEDULE_AFTER_FIRE, request.rescheduleAfterFire)
            putBoundary(boundary)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            allocation.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, request.triggerAtUtcMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, request.triggerAtUtcMillis, pendingIntent)
        }
        log.d {
            "Scheduled ${request.identity.kind} reminder key=${request.identity.semanticKey} " +
                "id=${allocation.notificationId} at ${request.triggerAtUtcMillis} (exact=$canExact)"
        }
    }

    actual override suspend fun cancel(semanticKey: String) {
        keyStore.record(semanticKey)?.let { allocation ->
            cancelPlatform(allocation, removeFromStore = true)
        }
    }

    actual override suspend fun cancelPendingReminders(eventId: String) {
        cleanupLegacyOnce(eventId)
        keyStore.recordsForEvent(eventId)
            .filter { it.lifecycle == ReminderLifecycle.PENDING && it.kind != ReminderKind.ONGOING }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    actual override suspend fun cancelReminders(eventId: String) {
        cleanupLegacyOnce(eventId)
        keyStore.recordsForEvent(eventId)
            .filter { it.kind != ReminderKind.ONGOING }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    actual override suspend fun cancelAll(eventId: String) {
        cancelReminders(eventId)
        stopOngoing(eventId)
    }

    actual override suspend fun startOngoing(identity: ReminderIdentity, title: String) =
        withCurrentHeldReminderBoundary { boundary ->
            startOngoingWithinHeldBoundary(identity, title, boundary)
        }

    private suspend fun startOngoingWithinHeldBoundary(
        identity: ReminderIdentity,
        title: String,
        boundary: AccountBoundary,
    ) {
        cleanupLegacyOnce(identity.eventId)
        stopOngoingRecords(identity.eventId)
        val allocation = keyStore.allocatePending(identity)
        val notification = buildOngoingNotification(identity, title, allocation.notificationId, boundary)
        try {
            NotificationManagerCompat.from(context).notify(allocation.notificationId, notification)
            keyStore.markDisplayed(identity.semanticKey)
        } catch (e: SecurityException) {
            keyStore.remove(identity.semanticKey)
            log.e(e) { "Failed to post ongoing notification for ${identity.eventId}" }
        }
    }

    actual override suspend fun stopOngoing(eventId: String) {
        stopOngoingRecords(eventId)
    }

    private suspend fun stopOngoingRecords(eventId: String) {
        keyStore.recordsForEvent(eventId)
            .filter { it.kind == ReminderKind.ONGOING }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    actual override suspend fun cancelAllAccountReminders() {
        keyStore.allRecords().forEach { allocation ->
            cancelPlatform(allocation, removeFromStore = true)
        }
        NotificationManagerCompat.from(context).cancelAll()
    }

    actual override suspend fun replacePendingReminders(requests: List<ReminderRequest>) {
        withCurrentHeldReminderBoundary { boundary ->
            val replacementKeys = requests.mapTo(mutableSetOf()) { it.identity.semanticKey }
            pendingReplacementRecordsToCancel(keyStore.allRecords(), replacementKeys)
                .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
            requests.forEach { request -> scheduleWithinHeldBoundary(request, boundary) }
        }
    }

    private suspend fun <T> withCurrentHeldReminderBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T {
        val expectedBoundary = boundaryGuard.activeBoundary()
            ?: throw IllegalStateException("Cannot schedule a reminder without an active account boundary")
        return withHeldReminderBoundary(
            mutationGate = mutationGate,
            activeBoundary = boundaryGuard::activeBoundary,
            expectedBoundary = expectedBoundary,
            block = block,
        )
    }

    /** Called only after the receiver has validated current persisted delivery truth. */
    suspend fun markAlarmDisplayed(semanticKey: String): Int? =
        keyStore.markDisplayed(semanticKey)?.notificationId

    /** Cancels prior delivered reminders for an event without touching future alarms. */
    suspend fun cancelDisplayedReminders(eventId: String, exceptSemanticKey: String? = null) {
        keyStore.recordsForEvent(eventId)
            .filter {
                it.lifecycle == ReminderLifecycle.DISPLAYED &&
                    it.kind != ReminderKind.ONGOING &&
                    it.semanticKey != exceptSemanticKey
            }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    /** Removes only identities at or before a delivered occurrence. */
    suspend fun cancelObsoleteOccurrenceReminders(
        eventId: String,
        occurrenceUtcMillis: Long,
    ) {
        cleanupLegacyOnce(eventId)
        keyStore.recordsForEvent(eventId)
            .filter { record ->
                ReminderIdentity.fromSemanticKey(record.semanticKey)
                    ?.occurrenceUtcMillis
                    ?.let { it <= occurrenceUtcMillis } == true
            }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    /** Removes every reminder identity for one validated occurrence. */
    suspend fun cancelOccurrenceReminders(
        eventId: String,
        occurrenceUtcMillis: Long,
    ) {
        cleanupLegacyOnce(eventId)
        keyStore.recordsForEvent(eventId)
            .filter { record ->
                ReminderIdentity.fromSemanticKey(record.semanticKey)
                    ?.occurrenceUtcMillis == occurrenceUtcMillis
            }
            .forEach { allocation -> cancelPlatform(allocation, removeFromStore = true) }
    }

    private suspend fun cleanupLegacyOnce(eventId: String) {
        keyStore.cleanupLegacyOnce(eventId) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            LEGACY_REMINDER_SLOTS.forEach { slot ->
                val notificationId = legacyNotificationId(eventId, slot)
                val legacyIntent = context.appComponentIntent(NOTIFICATION_RECEIVER_CLASS)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    legacyIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
                notificationManager.cancel(notificationId)
            }
            val legacyOngoingId = legacyNotificationId(eventId, LEGACY_ONGOING_SLOT)
            notificationManager.cancel(legacyOngoingId)
        }
    }

    private suspend fun cancelPlatform(
        allocation: ReminderKeyRecord,
        removeFromStore: Boolean,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = context.appComponentIntent(NOTIFICATION_RECEIVER_CLASS).apply {
            data = pendingIntentUri(allocation.semanticKey, ROLE_ALARM)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            allocation.notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        NotificationManagerCompat.from(context).cancel(allocation.notificationId)
        if (removeFromStore) keyStore.remove(allocation.semanticKey)
    }

    private fun buildOngoingNotification(
        identity: ReminderIdentity,
        title: String,
        notificationId: Int,
        boundary: AccountBoundary,
    ): android.app.Notification {
        val tapIntent = context.appComponentIntent(MAIN_ACTIVITY_CLASS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = pendingIntentUri(identity.semanticKey, ROLE_ONGOING_TAP)
            putExtra(EXTRA_TASK_ID, identity.eventId)
            putExtra(EXTRA_SEMANTIC_KEY, identity.semanticKey)
            putExtra(EXTRA_NOTIFICATION_AT_UTC, System.currentTimeMillis())
            putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, identity.occurrenceUtcMillis)
            putBoundary(boundary)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val gotItIntent = context.appComponentIntent(NOTIFICATION_ACTION_RECEIVER_CLASS).apply {
            action = ACTION_GOT_IT
            data = pendingIntentUri(identity.semanticKey, ROLE_GOT_IT)
            putExtra(EXTRA_TASK_ID, identity.eventId)
            putExtra(EXTRA_SEMANTIC_KEY, identity.semanticKey)
            putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, identity.occurrenceUtcMillis)
            putBoundary(boundary)
        }
        val gotItPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            gotItIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(context.appDrawable("ic_notification"))
            .setContentTitle(title)
            .setContentText(context.appString("notification_all_day_task_in_progress"))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .addAction(
                0,
                context.appString("notification_action_mark_done"),
                markDonePendingIntent(
                    context,
                    identity.eventId,
                    identity.taskWriteSemanticKey(),
                    notificationId,
                    identity.occurrenceUtcMillis,
                    boundary.accountId,
                    boundary.boundaryEpoch,
                ),
            )
            .addAction(0, context.appString("notification_action_got_it"), gotItPendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ONGOING_CHANNEL_ID = "all_day_tasks"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_SEMANTIC_KEY = "semantic_key"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_OCCURRENCE_DEADLINE_UTC = "occurrence_deadline_utc"
        const val EXTRA_NOTIFICATION_AT_UTC = "notification_at_utc"
        const val EXTRA_ALLOW_MARK_DONE = "allow_mark_done"
        const val EXTRA_RESCHEDULE_AFTER_FIRE = "reschedule_after_fire"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_BOUNDARY_EPOCH = "boundary_epoch"
        const val ACTION_MARK_DONE = "com.udnahc.opentasks.ACTION_MARK_DONE"
        const val ACTION_GOT_IT = "com.udnahc.opentasks.ACTION_GOT_IT"

        fun pendingIntentUri(semanticKey: String, role: String): Uri = Uri.Builder()
            .scheme("opentasks")
            .authority("reminder")
            .appendPath(role)
            .appendQueryParameter("key", semanticKey)
            .build()

        fun markDonePendingIntent(
            context: Context,
            eventId: String,
            semanticKey: String,
            notificationId: Int,
            occurrenceDeadlineUtcMillis: Long,
            accountId: String,
            boundaryEpoch: Long,
        ): PendingIntent {
            val intent = context.appComponentIntent(NOTIFICATION_ACTION_RECEIVER_CLASS).apply {
                action = ACTION_MARK_DONE
                data = pendingIntentUri(semanticKey, ROLE_MARK_DONE)
                putExtra(EXTRA_TASK_ID, eventId)
                putExtra(EXTRA_SEMANTIC_KEY, semanticKey)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_OCCURRENCE_DEADLINE_UTC, occurrenceDeadlineUtcMillis)
                putExtra(EXTRA_ACCOUNT_ID, accountId)
                putExtra(EXTRA_BOUNDARY_EPOCH, boundaryEpoch)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val ROLE_ALARM = "alarm"
        const val ROLE_TAP = "tap"
        const val ROLE_ONGOING_TAP = "ongoing_tap"
        private const val ROLE_MARK_DONE = "mark_done"
        private const val ROLE_GOT_IT = "got_it"
        private const val LEGACY_ONGOING_SLOT = 99
        private val LEGACY_REMINDER_SLOTS = 0 until LEGACY_ONGOING_SLOT

        private fun legacyNotificationId(eventId: String, slot: Int): Int =
            "$eventId:$slot".hashCode().and(0x7FFFFFFF)
    }
}

private fun Intent.putBoundary(boundary: AccountBoundary) {
    putExtra(NotificationScheduler.EXTRA_ACCOUNT_ID, boundary.accountId)
    putExtra(NotificationScheduler.EXTRA_BOUNDARY_EPOCH, boundary.boundaryEpoch)
}

private fun ReminderIdentity.taskWriteSemanticKey(): String = ReminderIdentity(
    eventId = eventId,
    occurrenceUtcMillis = occurrenceUtcMillis,
    kind = ReminderKind.DATE,
    ordinal = 0,
).semanticKey

private fun Context.appComponentIntent(className: String): Intent =
    Intent().setClassName(packageName, className)

private fun Context.appString(name: String): String = getString(appResourceId("string", name))

private fun Context.appDrawable(name: String): Int = appResourceId("drawable", name)

private fun Context.appResourceId(type: String, name: String): Int =
    resources.getIdentifier(name, type, packageName)

package com.udnahc.opentasks.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.action.settings.ConfigurePocketBaseUrlAction
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("SyncWorker")

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncService: SyncService by inject()
    private val configurePocketBaseUrlAction: ConfigurePocketBaseUrlAction by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()

    override suspend fun doWork(): Result {
        log.d { "SyncWorker starting" }
        return try {
            runScheduledSyncMaintenance(
                configureNetwork = { configurePocketBaseUrlAction() },
                syncNetwork = syncService::syncAll,
                rebuildReminders = { rebuildReminderQueueAction() },
                refreshWidgets = {
                    TaskWidget.refreshAllWidgets(applicationContext)
                    CalendarWidget.refreshAllWidgets(applicationContext)
                    WeekWidget.refreshAllWidgets(applicationContext)
                },
            )
            Result.success()
        } catch (e: Exception) {
            log.e(e) { "SyncWorker failed, retrying" }
            Result.retry()
        }
    }
}

/** Network configuration gates only the network pass; local reminder/widget maintenance always runs. */
internal suspend fun runScheduledSyncMaintenance(
    configureNetwork: suspend () -> Boolean,
    syncNetwork: suspend () -> Unit,
    rebuildReminders: suspend () -> Unit,
    refreshWidgets: suspend () -> Unit,
) {
    if (configureNetwork()) {
        syncNetwork()
    } else {
        log.d { "SyncWorker skipped network sync: no saved PocketBase URL" }
    }
    rebuildReminders()
    refreshWidgets()
}

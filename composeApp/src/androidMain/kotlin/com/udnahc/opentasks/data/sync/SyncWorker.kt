package com.udnahc.opentasks.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.udnahc.opentasks.domain.action.countdown.RescheduleAllCountdownRemindersAction
import com.udnahc.opentasks.domain.action.settings.ConfigurePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
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
    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction by inject()
    private val rescheduleAllCountdownRemindersAction: RescheduleAllCountdownRemindersAction by inject()

    override suspend fun doWork(): Result {
        log.d { "SyncWorker starting" }
        return try {
            if (!configurePocketBaseUrlAction()) {
                log.d { "SyncWorker skipped: no saved PocketBase URL" }
                return Result.success()
            }
            syncService.syncAll()
            rescheduleAllRemindersAction()
            rescheduleAllCountdownRemindersAction()
            TaskWidget.refreshAllWidgets(applicationContext)
            CalendarWidget.refreshAllWidgets(applicationContext)
            WeekWidget.refreshAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            log.e(e) { "SyncWorker failed, retrying" }
            Result.retry()
        }
    }
}

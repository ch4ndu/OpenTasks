package com.udnahc.opentasks.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncService: SyncService by inject()
    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction by inject()

    override suspend fun doWork(): Result {
        return try {
            syncService.syncAll()
            rescheduleAllRemindersAction()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

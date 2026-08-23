package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import org.lighthousegames.logging.logging

private val log = logging("BootReceiver")

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isReminderRebuildIntentAction(intent.action)) return
        log.d { "System time or package event received, enqueuing reminder rebuild" }
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            REMINDER_REBUILD_WORK_NAME,
            reminderRebuildExistingWorkPolicy(),
            reminderRebuildWorkRequest(),
        )
    }
}

internal fun isReminderRebuildIntentAction(action: String?): Boolean = action in setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
)

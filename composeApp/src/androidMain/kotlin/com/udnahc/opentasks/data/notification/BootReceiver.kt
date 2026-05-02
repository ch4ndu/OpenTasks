package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.domain.action.countdown.RescheduleAllCountdownRemindersAction
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("BootReceiver")

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction by inject()
    private val rescheduleAllCountdownRemindersAction: RescheduleAllCountdownRemindersAction by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        log.d { "Boot completed, rescheduling all reminders" }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAllRemindersAction()
                rescheduleAllCountdownRemindersAction()
            } catch (e: Exception) {
                log.e { "Failed to reschedule reminders on boot: ${e.message}" }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

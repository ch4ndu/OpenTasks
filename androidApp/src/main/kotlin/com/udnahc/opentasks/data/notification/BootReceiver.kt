package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("BootReceiver")

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()
    private val notificationScheduler: NotificationScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> Unit
            else -> return
        }
        log.d { "System time or package event received, rescheduling all reminders" }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rebuilt = accountBoundaryExecutor.withAuthenticatedBoundary {
                    try {
                        rebuildReminderQueueAction()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        try {
                            notificationScheduler.cancelAllAccountReminders()
                        } catch (cleanupError: CancellationException) {
                            throw cleanupError
                        } catch (_: Exception) {
                            // Preserve the original rebuild failure for the outer logger.
                        }
                        throw error
                    }
                }
                if (rebuilt == null) {
                    log.d { "Skipping reminder rebuild without an authenticated account session" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Failed to reschedule reminders on boot" }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

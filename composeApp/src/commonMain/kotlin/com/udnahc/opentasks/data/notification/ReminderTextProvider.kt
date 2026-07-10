package com.udnahc.opentasks.data.notification

import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.countdown_notification_in_day
import opentasks.composeapp.generated.resources.countdown_notification_in_days
import opentasks.composeapp.generated.resources.countdown_notification_in_hour
import opentasks.composeapp.generated.resources.countdown_notification_in_hours
import opentasks.composeapp.generated.resources.countdown_notification_in_minutes
import opentasks.composeapp.generated.resources.countdown_notification_today
import opentasks.composeapp.generated.resources.task_reminder_due_in_day
import opentasks.composeapp.generated.resources.task_reminder_due_in_days
import opentasks.composeapp.generated.resources.task_reminder_due_in_hour
import opentasks.composeapp.generated.resources.task_reminder_due_in_hours
import opentasks.composeapp.generated.resources.task_reminder_due_in_minutes
import opentasks.composeapp.generated.resources.task_reminder_due_now
import opentasks.composeapp.generated.resources.task_reminder_ending_now
import opentasks.composeapp.generated.resources.task_reminder_overdue
import opentasks.composeapp.generated.resources.task_reminder_starting_in_hour
import opentasks.composeapp.generated.resources.task_reminder_starting_in_hours
import opentasks.composeapp.generated.resources.task_reminder_starting_in_minutes
import opentasks.composeapp.generated.resources.task_reminder_starting_now
import org.jetbrains.compose.resources.getString

interface ReminderTextProvider {
    suspend fun taskDue(minutes: Int): String
    suspend fun taskStarting(minutes: Int): String
    suspend fun taskEndingNow(): String
    suspend fun taskOverdue(): String
    suspend fun countdownDue(minutes: Int): String
}

/** Resource-free fallback used by domain-only hosts and tests. App DI supplies the localized provider. */
object PlainReminderTextProvider : ReminderTextProvider {
    override suspend fun taskDue(minutes: Int): String = "Due in $minutes minutes"
    override suspend fun taskStarting(minutes: Int): String = "Starting in $minutes minutes"
    override suspend fun taskEndingNow(): String = "Ending now"
    override suspend fun taskOverdue(): String = "Overdue"
    override suspend fun countdownDue(minutes: Int): String = "Due in $minutes minutes"
}

class LocalizedReminderTextProvider : ReminderTextProvider {
    override suspend fun taskDue(minutes: Int): String = when {
        minutes == 0 -> getString(Res.string.task_reminder_due_now)
        minutes < 60 -> getString(Res.string.task_reminder_due_in_minutes, minutes)
        minutes < MINUTES_PER_DAY -> {
            val hours = minutes / MINUTES_PER_HOUR
            getString(
                if (hours == 1) Res.string.task_reminder_due_in_hour
                else Res.string.task_reminder_due_in_hours,
                hours,
            )
        }
        else -> {
            val days = minutes / MINUTES_PER_DAY
            getString(
                if (days == 1) Res.string.task_reminder_due_in_day
                else Res.string.task_reminder_due_in_days,
                days,
            )
        }
    }

    override suspend fun taskStarting(minutes: Int): String = when {
        minutes == 0 -> getString(Res.string.task_reminder_starting_now)
        minutes < 60 -> getString(Res.string.task_reminder_starting_in_minutes, minutes)
        else -> {
            val hours = minutes / MINUTES_PER_HOUR
            getString(
                if (hours == 1) Res.string.task_reminder_starting_in_hour
                else Res.string.task_reminder_starting_in_hours,
                hours,
            )
        }
    }

    override suspend fun taskEndingNow(): String =
        getString(Res.string.task_reminder_ending_now)

    override suspend fun taskOverdue(): String =
        getString(Res.string.task_reminder_overdue)

    override suspend fun countdownDue(minutes: Int): String = when {
        minutes == 0 -> getString(Res.string.countdown_notification_today)
        minutes < 60 -> getString(Res.string.countdown_notification_in_minutes, minutes)
        minutes < MINUTES_PER_DAY -> {
            val hours = minutes / MINUTES_PER_HOUR
            getString(
                if (hours == 1) Res.string.countdown_notification_in_hour
                else Res.string.countdown_notification_in_hours,
                hours,
            )
        }
        else -> {
            val days = minutes / MINUTES_PER_DAY
            getString(
                if (days == 1) Res.string.countdown_notification_in_day
                else Res.string.countdown_notification_in_days,
                days,
            )
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 1440
    }
}

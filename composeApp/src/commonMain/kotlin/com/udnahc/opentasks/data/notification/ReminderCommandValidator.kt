package com.udnahc.opentasks.data.notification

/** Commands carried by notification actions, taps, and delivered reminders. */
enum class ReminderCommand {
    MARK_DONE,
    GOT_IT,
    SHEET_DISMISS,
    ONGOING_TAP,
    TASK_TAP,
    DELIVERY,
    COUNTDOWN_TAP,
    COUNTDOWN_DELIVERY,
}

sealed interface ReminderCommandValidation {
    data class Accepted(val identity: ReminderIdentity) : ReminderCommandValidation

    data object Rejected : ReminderCommandValidation
}

/**
 * Validates the complete reminder command contract at the system boundary.
 * The semantic key is the authority for kind, ordinal, event, and occurrence;
 * duplicated payload fields must agree with it exactly.
 */
fun validateReminderCommand(
    command: ReminderCommand,
    semanticKey: String?,
    eventId: String?,
    occurrenceUtcMillis: Long?,
    accountId: String?,
    boundaryEpoch: Long,
): ReminderCommandValidation {
    val key = semanticKey?.takeIf { it.isNotBlank() } ?: return ReminderCommandValidation.Rejected
    val identity = ReminderIdentity.fromSemanticKey(key)
        ?: return ReminderCommandValidation.Rejected
    if (identity.semanticKey != key || identity.eventId.isBlank() || identity.ordinal < 0) {
        return ReminderCommandValidation.Rejected
    }
    if (eventId.isNullOrBlank() || eventId != identity.eventId) {
        return ReminderCommandValidation.Rejected
    }
    if (occurrenceUtcMillis == null || occurrenceUtcMillis <= 0L ||
        occurrenceUtcMillis != identity.occurrenceUtcMillis
    ) {
        return ReminderCommandValidation.Rejected
    }
    if (accountId.isNullOrBlank() || boundaryEpoch <= 0L) {
        return ReminderCommandValidation.Rejected
    }
    if (!command.accepts(identity)) return ReminderCommandValidation.Rejected
    return ReminderCommandValidation.Accepted(identity)
}

private fun ReminderCommand.accepts(identity: ReminderIdentity): Boolean = when (this) {
    ReminderCommand.MARK_DONE -> identity.kind in TASK_REMINDER_KINDS
    ReminderCommand.GOT_IT,
    ReminderCommand.ONGOING_TAP,
    -> identity.kind == ReminderKind.ONGOING
    ReminderCommand.SHEET_DISMISS ->
        identity.kind in TASK_REMINDER_KINDS || identity.kind == ReminderKind.ONGOING
    ReminderCommand.TASK_TAP,
    ReminderCommand.DELIVERY,
    -> identity.kind in TASK_REMINDER_KINDS
    ReminderCommand.COUNTDOWN_TAP,
    ReminderCommand.COUNTDOWN_DELIVERY,
    -> identity.kind == ReminderKind.COUNTDOWN
}

private val TASK_REMINDER_KINDS = setOf(
    ReminderKind.DATE,
    ReminderKind.DURATION,
    ReminderKind.OVERDUE,
)

class ReminderCommandRejectedException : IllegalStateException(
    "Notification command was rejected because its identity or account boundary was invalid",
)

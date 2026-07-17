package com.udnahc.opentasks

/**
 * A widget launch request. [id] must increase for every received Android intent so an
 * identical widget action is still handled as a new navigation event.
 */
data class WidgetNavigationEvent(
    val id: Long,
    val action: WidgetNavigationAction,
    val taskId: String? = null,
    val calendarDate: WidgetCalendarDate? = null,
)

enum class WidgetNavigationAction {
    CREATE_TASK,
    VIEW_LIST,
    VIEW_TASK,
    VIEW_CALENDAR,
}

data class WidgetCalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    val isValid: Boolean
        get() = year > 0 && month in 1..12 && day in 1..31
}

/** State holder for platform intent boundaries; identical valid requests still receive new IDs. */
class WidgetNavigationEventPublisher {
    private var lastEventId = 0L

    fun publish(
        action: WidgetNavigationAction,
        taskId: String? = null,
        calendarDate: WidgetCalendarDate? = null,
    ): WidgetNavigationEvent? {
        val normalizedTaskId = taskId?.takeIf { it.isNotBlank() }
        val normalizedDate = calendarDate?.takeIf { it.isValid }
        if (action == WidgetNavigationAction.VIEW_TASK && normalizedTaskId == null) return null
        if (action == WidgetNavigationAction.VIEW_CALENDAR && normalizedDate == null) return null
        lastEventId += 1
        return WidgetNavigationEvent(
            id = lastEventId,
            action = action,
            taskId = normalizedTaskId,
            calendarDate = normalizedDate,
        )
    }
}

/** Clears only the exact event acknowledged by the mounted Calendar entry. */
internal fun consumeCalendarNavigationEvent(
    current: WidgetNavigationEvent?,
    consumedEventId: Long,
): WidgetNavigationEvent? =
    current?.takeUnless { it.id == consumedEventId }

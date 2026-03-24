package com.udnahc.opentasks.ui.preview

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority

object PreviewSampleData {
    const val SAMPLE_YEAR = 2026
    const val SAMPLE_MONTH = 3
    const val SAMPLE_DAY = 16

    // Hardcoded UTC epoch millis to avoid kotlinx-datetime calls that crash Android Studio previews.
    // 2026-03-16T00:00Z, 2026-03-15T00:00Z (Sunday), deadlines at given UTC times.
    val sampleTodayMillis: Long = 1773619200000L
    val sampleWeekSundayMillis: Long = 1773532800000L
    private const val DEADLINE_MAR_20 = 1774000800000L  // 2026-03-20T10:00Z
    private const val DEADLINE_MAR_25 = 1774449000000L  // 2026-03-25T14:30Z
    private const val DEADLINE_APR_1 = 1775034000000L   // 2026-04-01T09:00Z

    val sampleTasks = listOf(
        Task(
            id = "preview-1",
            title = "Fix critical production bug",
            content = "The login flow is broken on Android 14 devices",
            priority = TaskPriority.HIGH,
            isUrgent = true,
            isImportant = true,
            deadline = DEADLINE_MAR_20,
            notifyBeforeValue = 1,
            notifyBeforeUnit = NotifyBeforeUnit.DAYS,
        ),
        Task(
            id = "preview-2",
            title = "Prepare quarterly report",
            content = "Compile metrics from Q1 and create presentation slides",
            priority = TaskPriority.HIGH,
            isUrgent = true,
            isImportant = true,
            deadline = DEADLINE_MAR_25,
            recurrenceType = RecurrenceType.MONTHLY,
        ),
        Task(
            id = "preview-3",
            title = "Review PR #428",
            content = "Database migration changes need review",
            priority = TaskPriority.MEDIUM,
            isUrgent = false,
            isImportant = true,
            deadline = DEADLINE_APR_1,
        ),
        Task(
            id = "preview-4",
            title = "Update documentation",
            content = "API docs are outdated after the refactor",
            priority = TaskPriority.MEDIUM,
            isUrgent = false,
            isImportant = true,
        ),
        Task(
            id = "preview-5",
            title = "Reply to vendor email",
            content = "They need confirmation on the contract terms",
            priority = TaskPriority.LOW,
            isUrgent = true,
            isImportant = false,
            deadline = DEADLINE_MAR_20,
        ),
        Task(
            id = "preview-6",
            title = "Book team lunch",
            content = "",
            priority = TaskPriority.LOW,
            isUrgent = true,
            isImportant = false,
        ),
        Task(
            id = "preview-7",
            title = "Organize desktop files",
            content = "Clean up downloads folder",
            priority = TaskPriority.NONE,
            isUrgent = false,
            isImportant = false,
        ),
        Task(
            id = "preview-8",
            title = "Learn Kotlin Multiplatform",
            content = "Watch the KotlinConf talks",
            priority = TaskPriority.NONE,
            isUrgent = false,
            isImportant = false,
            isCompleted = true,
        ),
    )

    val sampleTasksByDay: Map<Long, List<Task>> by lazy {
        sampleTasks.filter { it.deadline != null }.groupBy { dayKey(it.deadline!!) }
    }
}

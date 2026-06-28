package com.udnahc.opentasks.testutil

import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.TaskTag

fun testTask(
    id: String = "task-1",
    title: String = "Task $id",
    content: String = "Content $id",
    priority: TaskPriority = TaskPriority.NONE,
    deadline: Long? = null,
    endDeadline: Long? = null,
    status: TaskStatus = TaskStatus.TODO,
    isStarred: Boolean = false,
    section: String? = null,
    categoryId: String = AppConstants.DEFAULT_INBOX_ID,
    recurrenceType: RecurrenceType = RecurrenceType.NONE,
    recurrenceInterval: Int = 0,
    isAllDay: Boolean = false,
    sourceExternalId: String? = null,
    durationReminders: String = "",
    dateReminders: String = "",
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Task = Task(
    id = id,
    title = title,
    content = content,
    priority = priority,
    deadline = deadline,
    endDeadline = endDeadline,
    status = status,
    isStarred = isStarred,
    section = section,
    categoryId = categoryId,
    recurrenceType = recurrenceType,
    recurrenceInterval = recurrenceInterval,
    isAllDay = isAllDay,
    sourceExternalId = sourceExternalId,
    durationReminders = durationReminders,
    dateReminders = dateReminders,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testCategory(
    id: String = "category-1",
    name: String = "Category $id",
    icon: String = "inbox",
    sortOrder: Int = 0,
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Category = Category(
    id = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testNote(
    id: String = "note-1",
    title: String = "Note $id",
    content: String = "Content $id",
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Note = Note(
    id = id,
    title = title,
    content = content,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testTag(
    id: String = "tag-1",
    name: String = "Tag $id",
    color: String? = "#ff0000",
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Tag = Tag(
    id = id,
    name = name,
    color = color,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testTaskTag(
    taskId: String = "task-1",
    tagId: String = "tag-1",
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): TaskTag = TaskTag(
    taskId = taskId,
    tagId = tagId,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testAttachment(
    id: String = "attachment-1",
    ownerType: String = ATTACHMENT_OWNER_TASK,
    ownerId: String = "task-1",
    kind: String = ATTACHMENT_KIND_IMAGE,
    localPath: String = "/tmp/$id.jpg",
    thumbnailPath: String = "/tmp/${id}_thumb.jpg",
    remoteFileName: String? = null,
    mimeType: String = "image/jpeg",
    fileName: String = "$id.jpg",
    fileSizeBytes: Long = 100L,
    width: Int = 100,
    height: Int = 100,
    sortOrder: Int = 0,
    syncState: AttachmentSyncState = AttachmentSyncState.LOCAL_ONLY,
    lastSyncError: String? = null,
    pbId: String? = null,
    isSynced: Boolean = syncState == AttachmentSyncState.SYNCED,
    isDeleted: Boolean = false,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Attachment = Attachment(
    id = id,
    ownerType = ownerType,
    ownerId = ownerId,
    kind = kind,
    localPath = localPath,
    thumbnailPath = thumbnailPath,
    remoteFileName = remoteFileName,
    mimeType = mimeType,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    width = width,
    height = height,
    sortOrder = sortOrder,
    syncState = syncState,
    lastSyncError = lastSyncError,
    pbId = pbId,
    isSynced = isSynced,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testCountdown(
    id: String = "countdown-1",
    title: String = "Countdown $id",
    targetDate: Long = 1_000L,
    countdownType: CountdownType = CountdownType.COUNTDOWN,
    reminders: String = "",
    isCompleted: Boolean = false,
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    pbId: String? = null,
    createdAt: Long = 100L,
    updatedAt: Long = 100L,
): Countdown = Countdown(
    id = id,
    title = title,
    targetDate = targetDate,
    countdownType = countdownType,
    reminders = reminders,
    isCompleted = isCompleted,
    isDeleted = isDeleted,
    isSynced = isSynced,
    pbId = pbId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun testCalendarEvent(
    externalId: String = "event-1",
    title: String = "Event $externalId",
    description: String = "Description $externalId",
    startTimeUtcMillis: Long = 1_000L,
    endTimeUtcMillis: Long? = 2_000L,
    calendarName: String = "Calendar",
    isAllDay: Boolean = false,
): CalendarEvent = CalendarEvent(
    externalId = externalId,
    title = title,
    description = description,
    startTimeUtcMillis = startTimeUtcMillis,
    endTimeUtcMillis = endTimeUtcMillis,
    calendarName = calendarName,
    isAllDay = isAllDay,
)

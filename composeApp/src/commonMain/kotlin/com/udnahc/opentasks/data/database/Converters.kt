package com.udnahc.opentasks.data.database

import androidx.room.TypeConverter
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus

class Converters {

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType =
        RecurrenceType.entries.firstOrNull { it.name == value } ?: RecurrenceType.NONE

    @TypeConverter
    fun fromNotifyBeforeUnit(value: NotifyBeforeUnit): String = value.name

    @TypeConverter
    fun toNotifyBeforeUnit(value: String): NotifyBeforeUnit =
        NotifyBeforeUnit.entries.firstOrNull { it.name == value } ?: NotifyBeforeUnit.NONE

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority =
        TaskPriority.entries.firstOrNull { it.name == value } ?: TaskPriority.NONE

    @TypeConverter
    fun fromCountdownType(value: CountdownType): String = value.name

    @TypeConverter
    fun toCountdownType(value: String): CountdownType =
        CountdownType.entries.firstOrNull { it.name == value } ?: CountdownType.COUNTDOWN

    @TypeConverter
    fun fromCountingMode(value: CountingMode): String = value.name

    @TypeConverter
    fun toCountingMode(value: String): CountingMode =
        CountingMode.entries.firstOrNull { it.name == value } ?: CountingMode.COUNTDOWN

    @TypeConverter
    fun fromSmartListVisibility(value: SmartListVisibility): String = value.name

    @TypeConverter
    fun toSmartListVisibility(value: String): SmartListVisibility =
        SmartListVisibility.entries.firstOrNull { it.name == value } ?: SmartListVisibility.ALWAYS

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus =
        TaskStatus.entries.firstOrNull { it.name == value } ?: TaskStatus.TODO

    @TypeConverter
    fun fromAttachmentSyncState(value: AttachmentSyncState): String = value.name

    @TypeConverter
    fun toAttachmentSyncState(value: String): AttachmentSyncState =
        AttachmentSyncState.entries.firstOrNull { it.name == value } ?: AttachmentSyncState.LOCAL_ONLY
}

package com.udnahc.opentasks.data.database

import androidx.room.TypeConverter
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.data.model.TaskPriority

class Converters {

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun fromNotifyBeforeUnit(value: NotifyBeforeUnit): String = value.name

    @TypeConverter
    fun toNotifyBeforeUnit(value: String): NotifyBeforeUnit = NotifyBeforeUnit.valueOf(value)

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = TaskPriority.valueOf(value)

    @TypeConverter
    fun fromCountdownType(value: CountdownType): String = value.name

    @TypeConverter
    fun toCountdownType(value: String): CountdownType = CountdownType.valueOf(value)

    @TypeConverter
    fun fromCountingMode(value: CountingMode): String = value.name

    @TypeConverter
    fun toCountingMode(value: String): CountingMode = CountingMode.valueOf(value)

    @TypeConverter
    fun fromSmartListVisibility(value: SmartListVisibility): String = value.name

    @TypeConverter
    fun toSmartListVisibility(value: String): SmartListVisibility = SmartListVisibility.valueOf(value)
}

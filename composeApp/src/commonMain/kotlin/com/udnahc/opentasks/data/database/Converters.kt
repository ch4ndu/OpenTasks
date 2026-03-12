package com.udnahc.opentasks.data.database

import androidx.room.TypeConverter
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType

class Converters {

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun fromNotifyBeforeUnit(value: NotifyBeforeUnit): String = value.name

    @TypeConverter
    fun toNotifyBeforeUnit(value: String): NotifyBeforeUnit = NotifyBeforeUnit.valueOf(value)
}

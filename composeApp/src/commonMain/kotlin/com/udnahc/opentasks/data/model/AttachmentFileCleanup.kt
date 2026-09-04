package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachment_file_cleanup")
data class AttachmentFileCleanup(
    @PrimaryKey val path: String,
)

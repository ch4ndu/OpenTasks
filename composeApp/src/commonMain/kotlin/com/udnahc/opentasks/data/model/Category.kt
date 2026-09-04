package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

@Entity(
    tableName = "categories",
    indices = [
        Index("isDeleted", "sortOrder"),
        Index("name"),
        Index("isSynced"),
        Index("pbId"),
    ],
)
data class Category(
    @PrimaryKey val id: String = uuid4(),
    val name: String,
    val icon: String = "inbox",
    val sortOrder: Int = 0,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** The installation-created placeholder may yield to an authoritative remote Inbox. */
internal fun Category.isPristineInboxPlaceholder(): Boolean =
    id == AppConstants.DEFAULT_INBOX_ID &&
        name == "Inbox" &&
        icon == "inbox" &&
        sortOrder == 0 &&
        pbId == null &&
        !isSynced &&
        !isDeleted &&
        createdAt == 0L &&
        updatedAt == 0L

package com.udnahc.opentasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedTaskPayload(
    val id: Long,
    val description: String = "",
    val url: String = "",
    val icsContent: String = "",
    val icsFileName: String = "shared.ics",
) {
    val hasTaskContent: Boolean
        get() = description.isNotBlank() || url.isNotBlank()

    val hasIcsContent: Boolean
        get() = icsContent.isNotBlank()
}

private val _sharedTaskPayload = MutableStateFlow<SharedTaskPayload?>(null)
val sharedTaskPayload: StateFlow<SharedTaskPayload?> = _sharedTaskPayload.asStateFlow()

fun publishSharedTaskPayload(
    id: Long,
    description: String = "",
    url: String = "",
    icsContent: String = "",
    icsFileName: String = "shared.ics",
) {
    val payload = SharedTaskPayload(
        id = id,
        description = description,
        url = url,
        icsContent = icsContent,
        icsFileName = icsFileName,
    )
    if (payload.hasTaskContent || payload.hasIcsContent) {
        _sharedTaskPayload.value = payload
    }
}

fun clearSharedTaskPayload(id: Long) {
    if (_sharedTaskPayload.value?.id == id) {
        _sharedTaskPayload.value = null
    }
}

package com.udnahc.opentasks.data.model

import com.udnahc.opentasks.data.extensions.uuid4
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lighthousegames.logging.logging

private val log = logging("SubtaskItem")

@Serializable
data class SubtaskItem(
    val id: String = uuid4(),
    val text: String = "",
    val isChecked: Boolean = false,
)

private val subtaskJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal fun String.toSubtaskItems(): List<SubtaskItem> {
    if (isBlank()) return emptyList()
    return runCatching { subtaskJson.decodeFromString<List<SubtaskItem>>(this) }
        .getOrElse {
            log.w(it) { "Failed to parse subtasks JSON" }
            emptyList()
        }
        .filter { it.text.isNotBlank() }
}

internal fun List<SubtaskItem>.toSubtasksJson(): String {
    val cleaned = filter { it.text.isNotBlank() }
        .map { it.copy(text = it.text.trim()) }
    return if (cleaned.isEmpty()) "" else subtaskJson.encodeToString(cleaned)
}

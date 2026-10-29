package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val WHITESPACE_REGEX = Regex("\\s+")

fun taskPreviewText(html: String): String =
    html
        .replace(HTML_TAG_REGEX, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

fun taskPreviewTextById(tasks: List<Task>): Map<String, String> =
    tasks.associate { task -> task.id to taskPreviewText(task.content) }

/** Compare only display inputs before parsing HTML; repository entities remain untouched. */
fun Flow<List<Task>>.taskPreviewTexts(): Flow<Map<String, String>> =
    map { tasks -> tasks.associate { it.id to it.content } }
        .distinctUntilChanged()
        .map { contents -> contents.mapValues { (_, content) -> taskPreviewText(content) } }

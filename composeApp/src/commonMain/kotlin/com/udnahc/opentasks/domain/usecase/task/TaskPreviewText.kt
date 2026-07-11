package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task

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

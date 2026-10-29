package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskDisplayInputTest {
    @Test
    fun previewInputsIgnoreMetadataAndStatusButEmitContentChangesAndRemoval() = runTest {
        val original = testTask(id = "one", content = "<b>First</b>", deadline = 1L)
        val synced = original.copy(pbId = "remote", isSynced = true, isStarred = true)
        val edited = synced.copy(content = "<i>Second</i>")
        val results = listOf(listOf(original), listOf(synced), listOf(edited), emptyList())
            .asFlow().taskPreviewTexts().toList()
        assertEquals(listOf(mapOf("one" to "First"), mapOf("one" to "Second"), emptyMap()), results)
    }

    @Test
    fun dueFormatterRunsOnlyForChangedDeadlineOrMembership() = runTest {
        val original = testTask(id = "one", deadline = 1L)
        val synced = original.copy(pbId = "remote", isSynced = true, isStarred = true, content = "Changed")
        val moved = synced.copy(deadline = 2L)
        var formatterCalls = 0
        val results = listOf(listOf(original), listOf(synced), listOf(moved), emptyList())
            .asFlow().distinctDueTextInputs()
            .map { tasks -> tasks.associate { formatterCalls++; it.id to it.deadline } }.toList()
        assertEquals(2, formatterCalls)
        assertEquals(listOf(mapOf("one" to 1L), mapOf("one" to 2L), emptyMap()), results)
    }
}

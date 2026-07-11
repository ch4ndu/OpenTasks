package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskPreviewTextTest {
    @Test
    fun stripsMarkupAndDecodesSupportedEntitiesBeforeRowComposition() {
        assertEquals(
            "Plan & ship <today>",
            taskPreviewText("<p>Plan&nbsp;&amp; <strong>ship</strong> &lt;today&gt;</p>"),
        )
    }

    @Test
    fun buildsStablePreviewLookupByTaskId() {
        val previews = taskPreviewTextById(
            listOf(testTask(id = "one", content = "<b>First</b>")),
        )

        assertEquals(mapOf("one" to "First"), previews)
    }
}

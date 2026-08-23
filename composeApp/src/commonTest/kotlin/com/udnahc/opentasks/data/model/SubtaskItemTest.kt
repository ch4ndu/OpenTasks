package com.udnahc.opentasks.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtaskItemTest {
    @Test
    fun codecUsesStableSchemaOrderAndDefaults() {
        val item = SubtaskItem(id = "subtask-id", text = "Keep")

        assertEquals(
            """[{"id":"subtask-id","text":"Keep","isChecked":false}]""",
            listOf(item).toSubtasksJson(),
        )
        assertTrue(SubtaskItem().id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))

        val decodedWithDefaults = """[{"text":"From defaults"}]""".toSubtaskItems().single()
        assertEquals("From defaults", decodedWithDefaults.text)
        assertFalse(decodedWithDefaults.isChecked)
        assertTrue(decodedWithDefaults.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun encodingTrimsSavedTextAndDropsBlankRows() {
        val encoded = listOf(
            SubtaskItem(id = "blank", text = " \t "),
            SubtaskItem(id = "keep", text = "  Keep me  "),
            SubtaskItem(id = "done", text = " Done ", isChecked = true),
        ).toSubtasksJson()

        assertEquals(
            """[{"id":"keep","text":"Keep me","isChecked":false},{"id":"done","text":"Done","isChecked":true}]""",
            encoded,
        )
        assertEquals("", emptyList<SubtaskItem>().toSubtasksJson())
        assertEquals("", listOf(SubtaskItem(id = "blank", text = " ")).toSubtasksJson())
    }

    @Test
    fun decodingPreservesValidFieldsAndFiltersBlankRowsWithoutNormalizingText() {
        val decoded =
            """[{"id":"blank","text":"  ","isChecked":false},{"id":"keep","text":"  Preserve spacing  ","isChecked":true,"unknown":"ignored"}]"""
                .toSubtaskItems()

        assertEquals(
            listOf(SubtaskItem(id = "keep", text = "  Preserve spacing  ", isChecked = true)),
            decoded,
        )
    }

    @Test
    fun decodingBlankAndMalformedValuesFallsBackToAnEmptyList() {
        assertEquals(emptyList(), "".toSubtaskItems())
        assertEquals(emptyList(), "   ".toSubtaskItems())
        assertEquals(emptyList(), "not json".toSubtaskItems())
        assertEquals(emptyList(), """{"id":"not-an-array"}""".toSubtaskItems())
    }
}

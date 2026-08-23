package com.udnahc.opentasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedTaskPayloadTest {
    @Test
    fun claimRetiresOnlyTheExactIcsPayloadAndCannotClearANewerPayload() {
        val oldId = 90_001L
        val newerId = 90_002L
        clearSharedTaskPayload(oldId)
        clearSharedTaskPayload(newerId)

        try {
            publishSharedTaskPayload(oldId, icsContent = validIcs("old"))
            assertEquals(oldId, claimSharedIcsPayload(oldId)?.id)
            assertNull(sharedTaskPayload.value)

            publishSharedTaskPayload(newerId, icsContent = validIcs("new"))
            assertNull(claimSharedIcsPayload(oldId))
            clearSharedTaskPayload(oldId)
            assertEquals(newerId, sharedTaskPayload.value?.id)
        } finally {
            clearSharedTaskPayload(oldId)
            clearSharedTaskPayload(newerId)
        }
    }

    private fun validIcs(uid: String): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:$uid
        SUMMARY:Shared event
        DTSTART;VALUE=DATE:20260504
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}

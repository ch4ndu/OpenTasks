package com.udnahc.opentasks

import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MainActivityTest {

    @Test
    fun countdownNotificationParsingPreservesTheCompleteBoundaryEvent() {
        val event = assertNotNull(
            createNotificationDeepLinkEvent(
                eventId = "${COUNTDOWN_ID_PREFIX}countdown-a",
                occurrenceDeadlineUtcMillis = 100L,
                notificationAtUtcMillis = 200L,
                semanticKey = "semantic-key",
                accountId = "account-a",
                boundaryEpoch = 7L,
            )
        )

        assertEquals("${COUNTDOWN_ID_PREFIX}countdown-a", event.eventId)
        assertEquals(100L, event.occurrenceDeadlineUtcMillis)
        assertEquals(200L, event.notificationAtUtcMillis)
        assertEquals("semantic-key", event.semanticKey)
        assertEquals("account-a", event.accountId)
        assertEquals(7L, event.boundaryEpoch)
    }
}

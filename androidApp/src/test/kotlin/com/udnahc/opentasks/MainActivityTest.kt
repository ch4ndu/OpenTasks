package com.udnahc.opentasks

import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MainActivityTest {

    @Test
    fun canonicalTaskCountdownAndOngoingTapsPublishCompleteBoundaryEvents() {
        val task = ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0)
        val countdown = ReminderIdentity(
            "${COUNTDOWN_ID_PREFIX}countdown-a",
            200L,
            ReminderKind.COUNTDOWN,
            1,
        )
        val ongoing = ReminderIdentity("task-b", 300L, ReminderKind.ONGOING, 0)

        listOf(
            task to NotificationScheduler.ROLE_TAP,
            countdown to NotificationScheduler.ROLE_TAP,
            ongoing to NotificationScheduler.ROLE_ONGOING_TAP,
        ).forEach { (identity, role) ->
            val event = assertNotNull(createEvent(identity = identity, role = role))

            assertEquals(identity.eventId, event.eventId)
            assertEquals(identity.occurrenceUtcMillis, event.occurrenceDeadlineUtcMillis)
            assertEquals(identity.semanticKey, event.semanticKey)
            assertEquals("account-a", event.accountId)
            assertEquals(7L, event.boundaryEpoch)
        }
    }

    @Test
    fun tapUriRejectsWrongSchemeAuthorityRolePathAndKeyStructure() {
        val identity = ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0)
        val valid = tapUri(identity, NotificationScheduler.ROLE_TAP)
        val invalidUris = listOf(
            valid.copy(scheme = "other"),
            valid.copy(authority = "other"),
            valid.copy(encodedPath = "/other"),
            valid.copy(encodedPath = "/${NotificationScheduler.ROLE_TAP}/extra"),
            valid.copy(encodedFragment = "extra"),
            valid.copy(queryParameterNames = emptySet(), keyValues = emptyList()),
            valid.copy(queryParameterNames = setOf("key", "extra")),
            valid.copy(keyValues = listOf("other-key")),
            valid.copy(keyValues = listOf(identity.semanticKey, identity.semanticKey)),
        )

        invalidUris.forEach { uri ->
            assertNull(createEvent(identity = identity, uri = uri))
        }
    }

    @Test
    fun tapPayloadRejectsWrongKindEventOccurrenceAccountAndEpoch() {
        val task = ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0)
        val ongoing = ReminderIdentity("task-a", 100L, ReminderKind.ONGOING, 0)

        assertNull(
            createEvent(
                identity = task,
                eventId = "${COUNTDOWN_ID_PREFIX}countdown-a",
            )
        )
        assertNull(createEvent(identity = ongoing, role = NotificationScheduler.ROLE_TAP))
        assertNull(createEvent(identity = task, eventId = "other-task"))
        assertNull(createEvent(identity = task, occurrenceUtcMillis = 101L))
        assertNull(createEvent(identity = task, accountId = ""))
        assertNull(createEvent(identity = task, boundaryEpoch = 0L))
    }

    @Test
    fun opaqueUriParserResultCannotPublishATapEvent() {
        val identity = ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0)

        assertNull(createEvent(identity = identity, uri = null))
    }

    private fun createEvent(
        identity: ReminderIdentity,
        role: String = NotificationScheduler.ROLE_TAP,
        uri: AndroidReminderTapUri? = tapUri(identity, role),
        eventId: String = identity.eventId,
        occurrenceUtcMillis: Long = identity.occurrenceUtcMillis,
        accountId: String = "account-a",
        boundaryEpoch: Long = 7L,
    ) = createAndroidNotificationDeepLinkEvent(
        uri = uri,
        eventId = eventId,
        occurrenceDeadlineUtcMillis = occurrenceUtcMillis,
        notificationAtUtcMillis = occurrenceUtcMillis + 1L,
        semanticKey = identity.semanticKey,
        accountId = accountId,
        boundaryEpoch = boundaryEpoch,
    )

    private fun tapUri(identity: ReminderIdentity, role: String) = AndroidReminderTapUri(
        scheme = "opentasks",
        authority = "reminder",
        encodedPath = "/$role",
        encodedFragment = null,
        queryParameterNames = setOf("key"),
        keyValues = listOf(identity.semanticKey),
    )
}

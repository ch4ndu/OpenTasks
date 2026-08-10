package com.udnahc.opentasks.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.udnahc.opentasks.data.auth.AccountBoundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetBoundaryMarkerTest {

    @Test
    fun writeAndClearPersistAndRemoveBothBoundaryFields() {
        val preferences = mutablePreferencesOf()
        val boundary = boundary("account-a", 4L)

        WidgetBoundaryMarker.write(preferences, boundary)

        assertEquals("account-a", preferences[WidgetBoundaryMarker.ACCOUNT_ID_KEY])
        assertEquals(4L, preferences[WidgetBoundaryMarker.BOUNDARY_EPOCH_KEY])
        assertEquals(
            WidgetBoundaryMarker.Value("account-a", 4L),
            WidgetBoundaryMarker.read(preferences),
        )

        WidgetBoundaryMarker.clear(preferences)

        assertNull(preferences[WidgetBoundaryMarker.ACCOUNT_ID_KEY])
        assertNull(preferences[WidgetBoundaryMarker.BOUNDARY_EPOCH_KEY])
        assertFalse(WidgetBoundaryMarker.matches(preferences, boundary))
    }

    @Test
    fun transitionLogoutAndReauthenticationClearRejectThePreviousBoundary() {
        val boundary = boundary("account-a", 7L)

        listOf("transition", "logout", "reauthentication").forEach {
            val preferences = mutablePreferencesOf()
            WidgetBoundaryMarker.write(preferences, boundary)
            assertTrue(WidgetBoundaryMarker.matches(preferences, boundary))

            WidgetBoundaryMarker.clear(preferences)

            assertFalse(WidgetBoundaryMarker.matches(preferences, boundary))
        }
    }

    @Test
    fun blankBetweenAccountsRejectsLateAccountAAndAcceptsAccountB() {
        val accountA = boundary("account-a", 1L)
        val accountB = boundary("account-b", 2L)
        val preferences = mutablePreferencesOf()

        WidgetBoundaryMarker.write(preferences, accountA)
        assertTrue(WidgetBoundaryMarker.matches(preferences, accountA))

        WidgetBoundaryMarker.clear(preferences)
        assertFalse(WidgetBoundaryMarker.matches(preferences, accountA))
        assertFalse(WidgetBoundaryMarker.matches(preferences, accountB))

        WidgetBoundaryMarker.write(preferences, accountB)
        assertFalse(WidgetBoundaryMarker.matches(preferences, accountA))
        assertTrue(WidgetBoundaryMarker.matches(preferences, accountB))
    }

    private fun boundary(accountId: String, boundaryEpoch: Long) = AccountBoundary(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = accountId,
        capabilityVersion = 2,
        boundaryEpoch = boundaryEpoch,
    )
}

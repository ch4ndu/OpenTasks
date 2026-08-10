package com.udnahc.opentasks.data.auth

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PocketBaseAccountAuthenticatorTest {

    @Test
    fun retryableHttpStatusesAreConnectivityFailuresInEveryAccountRequestPhase() {
        val retryableStatuses = listOf(408, 425, 429, 503)

        AccountHttpRequestPhase.entries.forEach { phase ->
            retryableStatuses.forEach { statusCode ->
                val failure = classifyAccountHttpFailure(phase, statusCode)

                assertIs<AccountConnectivityException>(failure)
                assertTrue(failure.cause?.message.orEmpty().contains(phase.diagnosticName))
                assertTrue(failure.cause?.message.orEmpty().contains("HTTP $statusCode"))
            }
        }
    }

    @Test
    fun explicitCredentialDenialRemainsAuthenticationRejection() {
        val failure = classifyAccountHttpFailure(
            AccountHttpRequestPhase.AUTHENTICATION,
            statusCode = 401,
        )

        assertIs<AccountAuthenticationRejectedException>(failure)
    }

    @Test
    fun nonTransientInvalidCapabilityResponsesRemainCapabilityRejections() {
        listOf(AccountHttpRequestPhase.CAPABILITY, AccountHttpRequestPhase.OWNER_INVENTORY)
            .forEach { phase ->
                assertIs<AccountCapabilityRejectedException>(
                    classifyAccountHttpFailure(phase, statusCode = 404),
                )
                assertIs<AccountCapabilityRejectedException>(
                    classifyAccountHttpFailure(phase, statusCode = 200),
                )
            }
    }
}

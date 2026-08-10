package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.sync.canonicalUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountEndpointTest {

    @Test
    fun allowsLoopbackAndRfc1918HttpEndpoints() {
        val endpoints = listOf(
            "http://localhost:8090" to "http://localhost:8090",
            "http://127.0.0.1:8090" to "http://127.0.0.1:8090",
            "http://10.0.0.1:8090" to "http://10.0.0.1:8090",
            "http://172.16.0.1:8090" to "http://172.16.0.1:8090",
            "http://172.31.255.254:8090" to "http://172.31.255.254:8090",
            "http://192.168.86.167:8090" to "http://192.168.86.167:8090",
        )

        endpoints.forEach { (input, expected) ->
            assertEquals(expected, canonicalizeAccountEndpoint(input).canonicalUrl)
        }
    }

    @Test
    fun rejectsCleartextPublicAndNonPrivateIpv4Endpoints() {
        listOf(
            "http://example.com:8090",
            "http://8.8.8.8:8090",
            "http://172.15.255.255:8090",
            "http://172.32.0.1:8090",
            "http://192.167.1.1:8090",
            "http://169.254.1.1:8090",
        ).forEach { endpoint ->
            assertFailsWith<IllegalArgumentException> {
                canonicalizeAccountEndpoint(endpoint)
            }
        }
    }

    @Test
    fun continuesToAllowHttpsAndCanonicalizeHostCase() {
        assertEquals(
            "https://tasks.example.com:443",
            canonicalizeAccountEndpoint("https://TASKS.EXAMPLE.COM").canonicalUrl,
        )
    }
}

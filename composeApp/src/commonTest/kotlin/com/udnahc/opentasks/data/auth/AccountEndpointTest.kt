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
            "http://[::1]:8090" to "http://[::1]:8090",
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

    @Test
    fun rejectsMalformedEndpointFormsAndInvalidExplicitPorts() {
        listOf(
            "tasks.example.com",
            "ftp://tasks.example.com",
            "https://tasks.example.com/path",
            "https://tasks.example.com?query=value",
            "https://tasks.example.com#fragment",
            "https://user@tasks.example.com",
            "https://",
            "https://:8090",
            "https://[::1",
            "https://tasks.example.com:",
            "https://tasks.example.com:invalid",
            "https://tasks.example.com:-1",
            "https://tasks.example.com:0",
            "https://tasks.example.com:65536",
            "https://tasks.example.com:999999999999999999",
        ).forEach { endpoint ->
            assertFailsWith<IllegalArgumentException> {
                canonicalizeAccountEndpoint(endpoint)
            }
        }
    }

    @Test
    fun rejectsMalformedHostSyntax() {
        listOf(
            "https://.",
            "https://example..com",
            "https://-bad.example",
            "https://bad-.example",
            "https://256.256.256.256",
            "https://[::::]",
        ).forEach { endpoint ->
            assertFailsWith<IllegalArgumentException> {
                canonicalizeAccountEndpoint(endpoint)
            }
        }
    }

    @Test
    fun rejectsDnsHostsBeyondLengthLimits() {
        val labelTooLong = "a".repeat(64)
        val hostTooLong = listOf(
            "a".repeat(63),
            "b".repeat(63),
            "c".repeat(63),
            "d".repeat(62),
        ).joinToString(".")

        listOf(labelTooLong, hostTooLong).forEach { host ->
            assertFailsWith<IllegalArgumentException> {
                canonicalizeAccountEndpoint("https://$host")
            }
        }
    }

    @Test
    fun acceptsRepresentativeDnsIpv4AndIpv6HostSyntax() {
        val endpoints = listOf(
            "https://tasks" to "tasks",
            "https://tasks.example.com" to "tasks.example.com",
            "https://192.0.2.10:8090" to "192.0.2.10",
            "https://[2001:db8::1]:8090" to "[2001:db8::1]",
            "https://[2001:0db8:0000:0000:0000:ff00:0042:8329]" to
                "[2001:0db8:0000:0000:0000:ff00:0042:8329]",
        )

        endpoints.forEach { (input, expectedHost) ->
            assertEquals(expectedHost, canonicalizeAccountEndpoint(input).host)
        }
    }
}

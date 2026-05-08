package com.udnahc.opentasks.data.sync

import io.ktor.http.URLProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

class PocketBaseClientProviderTest {
    @Test
    fun parsesExplicitPocketBasePort() {
        val endpoint = parsePocketBaseEndpoint("http://192.168.1.100:8090")

        assertEquals(URLProtocol.HTTP, endpoint.protocol)
        assertEquals("192.168.1.100", endpoint.host)
        assertEquals(8090, endpoint.port)
    }

    @Test
    fun httpsWithoutPortDefaultsTo443() {
        val endpoint = parsePocketBaseEndpoint("https://example.com")

        assertEquals(URLProtocol.HTTPS, endpoint.protocol)
        assertEquals("example.com", endpoint.host)
        assertEquals(443, endpoint.port)
    }

    @Test
    fun httpWithoutPortDefaultsTo80() {
        val endpoint = parsePocketBaseEndpoint("http://example.com")

        assertEquals(URLProtocol.HTTP, endpoint.protocol)
        assertEquals("example.com", endpoint.host)
        assertEquals(80, endpoint.port)
    }
}

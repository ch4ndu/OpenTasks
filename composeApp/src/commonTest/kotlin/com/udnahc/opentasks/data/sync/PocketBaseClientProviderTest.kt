package com.udnahc.opentasks.data.sync

import io.ktor.http.URLProtocol
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun replacementAndDisconnectCloseEachPublishedClientOnce() {
        val provider = PocketBaseClientProvider()
        provider.configure("http://first.example")
        val first = assertNotNull(provider.client)
        val firstJob = assertNotNull(first.httpClient.coroutineContext[Job])

        provider.configure("http://second.example")
        val second = assertNotNull(provider.client)
        val secondJob = assertNotNull(second.httpClient.coroutineContext[Job])

        assertFalse(firstJob.isActive)
        assertNull(PocketBaseClientProvider.endpointFor(first))
        assertEquals("second.example", provider.endpoint?.host)

        provider.disconnect()
        provider.disconnect()

        assertFalse(secondJob.isActive)
        assertNull(provider.client)
        assertNull(PocketBaseClientProvider.endpointFor(second))
    }
}

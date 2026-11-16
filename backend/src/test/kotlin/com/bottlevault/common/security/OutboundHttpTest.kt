// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.client.ResourceAccessException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class OutboundHttpTest {

    private fun server(handler: (String) -> Unit, delayMs: Long = 0): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                handler("${ex.requestMethod} ${ex.requestURI}")
                Thread.sleep(delayMs)
                val body = "ok".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            start()
        }

    @Test
    fun `requests go through the configured proxy`() {
        val seen = CopyOnWriteArrayList<String>()
        val proxy = server({ seen += it })
        try {
            val http = OutboundHttp("http://127.0.0.1:${proxy.address.port}", Duration.ofSeconds(2), Duration.ofSeconds(2))
            val body = http.restClient().get().uri("http://upstream.invalid/probe").retrieve().body(String::class.java)
            assertEquals("ok", body)
            assertEquals(listOf("GET http://upstream.invalid/probe"), seen.toList())
        } finally {
            proxy.stop(0)
        }
    }

    @Test
    fun `a slow upstream times out instead of holding a request thread`() {
        val slow = server({}, delayMs = 3_000)
        try {
            val http = OutboundHttp("", Duration.ofSeconds(1), Duration.ofMillis(500))
            assertThrows(ResourceAccessException::class.java) {
                http.restClient().get().uri("http://127.0.0.1:${slow.address.port}/").retrieve().body(String::class.java)
            }
        } finally {
            slow.stop(0)
        }
    }

    @Test
    fun `a proxy setting that is not http host port fails at startup`() {
        assertThrows(IllegalArgumentException::class.java) { OutboundHttp.proxySelector("egress:3128") }
        assertThrows(IllegalArgumentException::class.java) { OutboundHttp.proxySelector("https://egress") }
    }
}

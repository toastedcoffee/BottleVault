// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.common.exception.ApiException
import com.bottlevault.common.security.ClientIp
import com.bottlevault.common.security.ClientIpResolver
import com.bottlevault.common.security.IdentifierHasher
import com.bottlevault.common.security.OutboundHttp
import com.bottlevault.common.security.SecurityEvents
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Duration

class TurnstileVerifierTest {
    private var lastForm = ""
    private var reply: Pair<Int, String> = 200 to """{"success":true,"action":"login","hostname":"cellar.example.com"}"""
    private val stub: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/siteverify") { ex ->
            lastForm = ex.requestBody.readAllBytes().decodeToString()
            val body = reply.second.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(reply.first, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        start()
    }

    @AfterEach
    fun stop() = stub.stop(0)

    private val hasher = IdentifierHasher("k")
    private val events = SecurityEvents(ClientIpResolver(hasher, ""), hasher, Clock.systemUTC(), JsonMapper.builder().build(), "")
    private val client = ClientIp("203.0.113.7", "203.0.113.7", "h")

    private fun verifier(secret: String = "s3cret", hostname: String = "cellar.example.com", url: String = "http://127.0.0.1:${stub.address.port}/siteverify") =
        TurnstileVerifier(OutboundHttp("", Duration.ofSeconds(2), Duration.ofSeconds(2)), events, secret, hostname, url)

    private fun statusOf(block: () -> Unit): HttpStatus = assertThrows(ApiException::class.java) { block() }.status

    @Test
    fun `a valid token for the right action and host passes, and Cloudflare sees the visitor's address`() {
        verifier().requireHuman("token", "login", client)
        assertTrue(lastForm.contains("remoteip=203.0.113.7"))
        assertTrue(lastForm.contains("response=token"))
        assertTrue(lastForm.contains("secret=s3cret"))
    }

    @Test
    fun `a missing token never reaches Cloudflare`() {
        lastForm = ""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman(null, "login", client) })
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("  ", "login", client) })
        assertEquals("", lastForm)
    }

    @Test
    fun `a failed challenge, the wrong action, or another site's token are all refused`() {
        reply = 200 to """{"success":false,"error-codes":["invalid-input-response"]}"""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("t", "login", client) })
        reply = 200 to """{"success":true,"action":"register","hostname":"cellar.example.com"}"""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("t", "login", client) })
        reply = 200 to """{"success":true,"action":"login","hostname":"evil.example.net"}"""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("t", "login", client) })
    }

    @Test
    fun `with no expected hostname configured, any hostname is accepted`() {
        reply = 200 to """{"success":true,"action":"login","hostname":"anything.example.org"}"""
        verifier(hostname = "").requireHuman("t", "login", client)
    }

    @Test
    fun `Cloudflare's published test keys pass even though their reply carries no action`() {
        reply = 200 to """{"success":true,"error-codes":[],"hostname":"example.com","metadata":{"result_with_testing_key":true}}"""
        verifier().requireHuman("XXXX.DUMMY.TOKEN.XXXX", "login", client)
        reply = 200 to """{"success":false,"error-codes":["invalid-input-response"],"metadata":{"result_with_testing_key":true}}"""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("t", "login", client) })
    }

    @Test
    fun `a reply without the testing marker still needs the right action`() {
        reply = 200 to """{"success":true,"error-codes":[],"hostname":"cellar.example.com","metadata":{"result_with_testing_key":false}}"""
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { verifier().requireHuman("t", "login", client) })
    }

    @Test
    fun `when Cloudflare cannot answer, sign-in fails closed`() {
        reply = 500 to "oops"
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statusOf { verifier().requireHuman("t", "login", client) })
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statusOf { verifier(url = "http://127.0.0.1:1/siteverify").requireHuman("t", "login", client) })
    }

    @Test
    fun `without a secret key Turnstile is off`() {
        verifier(secret = "").requireHuman(null, "login", client)
    }
}

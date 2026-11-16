// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Real HTTP, so the raw percent-encoded URI reaches Tomcat exactly as a client sent it. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["app.rate-limit.max-attempts=2"]
)
class RateLimitPathIntegrationTest : AbstractPostgresIntegrationTest() {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun post(rawPath: String): Int = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:$port$rawPath"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"nobody@example.com","password":"wrong-password"}"""))
            .build(),
        HttpResponse.BodyHandlers.discarding()
    ).statusCode()

    @Test
    fun `an encoded login path reaches the login handler and shares its bucket`() {
        // 400 (bad credentials), not 404: the encoded form really reaches login.
        assertEquals(400, post("/api/%61uth/log%69n"))
        assertEquals(400, post("/api/auth/login"))
        assertEquals(429, post("/api/auth/login"))
        assertEquals(429, post("/api/%61uth/log%69n"))
    }

    @Test
    fun `made-up auth paths share one bucket instead of minting a key each`() {
        assertEquals(404, post("/api/auth/x1"))
        assertEquals(404, post("/api/auth/x2"))
        assertEquals(429, post("/api/auth/x3"))
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Real HTTP through a running Tomcat, not MockMvc. Framework-generated errors
 * (unknown route, unreadable body, an unhandled exception) are rendered by the
 * servlet container's ERROR dispatch to /error, and MockMvc never performs that
 * dispatch, so no MockMvc test can see what the client actually receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchIntegrationTest : AbstractPostgresIntegrationTest() {

    /** An endpoint whose exception nothing handles, so it must surface as a 500. */
    @TestConfiguration
    class FailingEndpoint {
        @Bean
        fun failingController() = FailingController()
    }

    @RestController
    class FailingController {
        @GetMapping("/api/test-only/unhandled")
        fun unhandled(): String = throw IllegalStateException(INTERNAL_DETAIL)
    }

    companion object {
        const val INTERNAL_DETAIL = "internal-detail-that-must-not-reach-the-client"

        // One client for the class: JUnit makes a new instance per test method.
        private val http: HttpClient = HttpClient.newHttpClient()
    }

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // Registered only by the tests that need a signed-in user.
    private val bearer: String by lazy {
        val body = """{"email":"error-dispatch-${System.nanoTime()}@example.com","password":"password123"}"""
        val response = send("POST", "/api/auth/register", json = body)
        assertEquals(201, response.statusCode(), response.body())
        "Bearer " + objectMapper.readValue(response.body(), AuthResponse::class.java).accessToken
    }

    private fun send(method: String, path: String, json: String? = null, auth: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, json?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        json?.let { builder.header("Content-Type", "application/json") }
        auth?.let { builder.header("Authorization", it) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Boot's default error body carries status and path only: no message, no trace. */
    private fun assertNoInternals(response: HttpResponse<String>) {
        val body = response.body()
        assertFalse(body.contains("\"message\""), body)
        assertFalse(body.contains("\"trace\""), body)
        assertFalse(body.contains("\"exception\""), body)
    }

    @Test
    fun `an unknown endpoint is a 404, not a 401`() {
        val response = send("GET", "/api/no-such-endpoint", auth = bearer)
        assertEquals(404, response.statusCode())
        assertNoInternals(response)
    }

    @Test
    fun `an unreadable request body is a 400, not a 401`() {
        assertEquals(400, send("POST", "/api/bottles", json = "{bad", auth = bearer).statusCode())
    }

    @Test
    fun `an unhandled exception is a 500, not a 401, and its message stays on the server`() {
        val response = send("GET", "/api/test-only/unhandled", auth = bearer)
        assertEquals(500, response.statusCode())
        assertNoInternals(response)
        assertFalse(response.body().contains(INTERNAL_DETAIL), response.body())
    }

    @Test
    fun `a request without credentials is still a 401`() {
        assertEquals(401, send("GET", "/api/bottles").statusCode())
    }

    @Test
    fun `a direct request for the error page is still denied`() {
        // Only the container's internal ERROR dispatch is permitted. A client
        // asking for /error itself is an ordinary REQUEST dispatch.
        assertEquals(401, send("GET", "/error").statusCode())
    }
}

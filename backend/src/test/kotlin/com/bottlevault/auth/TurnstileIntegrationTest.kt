// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetSocketAddress

@SpringBootTest
@AutoConfigureMockMvc
class TurnstileIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    companion object {
        /** Accepts the token "human" for the action it was issued for; rejects everything else. */
        private val stub: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/siteverify") { ex ->
                val form = ex.requestBody.readAllBytes().decodeToString()
                val body = when {
                    form.contains("response=human-login") -> """{"success":true,"action":"login"}"""
                    form.contains("response=human-register") -> """{"success":true,"action":"register"}"""
                    else -> """{"success":false}"""
                }
                val bytes = body.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun turnstile(registry: DynamicPropertyRegistry) {
            registry.add("app.turnstile.secret-key") { "test-secret" }
            registry.add("app.turnstile.verify-url") { "http://127.0.0.1:${stub.address.port}/siteverify" }
        }
    }

    private fun login(token: String?) = mockMvc.perform(
        post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(
            """{"email":"nobody@example.com","password":"password123"${token?.let { ",\"turnstileToken\":\"$it\"" } ?: ""}}"""
        )
    )

    private fun register(email: String, token: String?) = mockMvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
            """{"email":"$email","password":"password123"${token?.let { ",\"turnstileToken\":\"$it\"" } ?: ""}}"""
        )
    )

    @Test
    fun `sign-in without a Turnstile token is refused before credentials are checked`() {
        login(null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Please complete the verification and try again."))
    }

    @Test
    fun `with a valid token the request reaches the credential check`() {
        login("human-login")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
    }

    @Test
    fun `registration needs a token issued for registration`() {
        val email = "turnstile-${System.nanoTime()}@example.com"
        register(email, null).andExpect(status().isBadRequest)
        register(email, "human-login").andExpect(status().isBadRequest)
        register(email, "human-register").andExpect(status().isCreated)
    }
}

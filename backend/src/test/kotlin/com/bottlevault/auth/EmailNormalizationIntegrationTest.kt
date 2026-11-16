// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest
@AutoConfigureMockMvc
class EmailNormalizationIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    private fun post(path: String, json: String) =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))

    @Test
    fun `case and spaces never create a second account and never block sign-in`() {
        val local = "Mixed.Case-${System.nanoTime()}"
        post("/api/auth/register", """{"email":"$local@Example.COM","password":"password123"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.email").value("${local.lowercase()}@example.com"))
        post("/api/auth/register", """{"email":"${local.lowercase()}@example.com","password":"password123"}""")
            .andExpect(status().isConflict)
        post("/api/auth/login", """{"email":"${local.uppercase()}@EXAMPLE.com","password":"password123"}""")
            .andExpect(status().isOk)
        post("/api/auth/login", """{"email":"  ${local.lowercase()}@example.com ","password":"password123"}""")
            .andExpect(status().isOk)
    }

    @Test
    fun `two registrations racing for one email give one account and a 409, never a 500`() {
        val email = "race-${System.nanoTime()}@example.com"
        val pool = Executors.newFixedThreadPool(2)
        try {
            val statuses = List(2) { i ->
                Callable { post("/api/auth/register", """{"email":"$email","password":"password123","displayName":"R$i"}""").andReturn().response.status }
            }.let { pool.invokeAll(it) }.map { it.get() }
            assertEquals(listOf(201, 409), statuses.sorted())
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `a password longer than bcrypt can use is refused, not silently truncated`() {
        val tooLong = "é".repeat(37) // 74 bytes in UTF-8, 37 characters
        post("/api/auth/register", """{"email":"long-pw-${System.nanoTime()}@example.com","password":"$tooLong"}""")
            .andExpect(status().isBadRequest)
    }
}

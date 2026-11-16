// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SuspensionIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var statusCache: UserStatusCache

    private val password = "password123"

    private fun register(email: String): AuthResponse {
        val result = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
    }

    private fun setSuspended(email: String, suspended: Boolean): UUID {
        val user = users.findByEmail(email)!!
        user.disabledAt = if (suspended) Instant.now() else null
        users.save(user)
        // Stands in for the cache's 60-second expiry.
        statusCache.forget(user.id!!)
        return user.id!!
    }

    @Test
    fun `a suspended account cannot sign in, refresh, or keep using its access token`() {
        val email = "suspended-${System.nanoTime()}@example.com"
        val auth = register(email)
        mockMvc.perform(get("/api/bottles").header("Authorization", "Bearer ${auth.accessToken}")).andExpect(status().isOk)

        setSuspended(email, true)

        mockMvc.perform(get("/api/bottles").header("Authorization", "Bearer ${auth.accessToken}")).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""{"refreshToken":"${auth.refreshToken}"}""")
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("This account is suspended"))
    }

    @Test
    fun `a wrong password on a suspended account reveals nothing`() {
        val email = "quiet-${System.nanoTime()}@example.com"
        register(email)
        setSuspended(email, true)
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email","password":"wrong-password"}""")
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Invalid email or password"))
    }

    @Test
    fun `lifting the suspension restores sign-in`() {
        val email = "restored-${System.nanoTime()}@example.com"
        register(email)
        setSuspended(email, true)
        setSuspended(email, false)
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email","password":"$password"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `a deleted account's access token stops working`() {
        val email = "deleted-${System.nanoTime()}@example.com"
        val auth = register(email)
        val user = users.findByEmail(email)!!
        users.delete(user)
        statusCache.forget(user.id!!)
        mockMvc.perform(get("/api/bottles").header("Authorization", "Bearer ${auth.accessToken}")).andExpect(status().isUnauthorized)
    }
}

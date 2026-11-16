// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.user

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
class ProfileLimitsIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    private lateinit var token: String

    @BeforeEach
    fun register() {
        val result = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"limits-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        token = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private fun profile(json: String) = mockMvc.perform(
        put("/api/user/profile").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(json)
    )

    @Test
    fun `profile values are bounded and well-formed`() {
        profile("""{"displayName":"${"x".repeat(101)}"}""").andExpect(status().isBadRequest)
        profile("""{"defaultCurrency":"dollars"}""").andExpect(status().isBadRequest)
        profile("""{"measurementUnit":"gallon"}""").andExpect(status().isBadRequest)
        profile("""{"displayName":"Fine","defaultCurrency":"EUR","measurementUnit":"cl"}""").andExpect(status().isOk)
    }

    @Test
    fun `a blank display name is stored as no name`() {
        profile("""{"displayName":"   "}""").andExpect(status().isOk).andExpect(jsonPath("$.displayName").doesNotExist())
    }

    @Test
    fun `a new password longer than bcrypt can hold is refused, not truncated`() {
        mockMvc.perform(
            put("/api/user/password").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"password123","newPassword":"${"é".repeat(40)}"}""")
        ).andExpect(status().isBadRequest)
    }
}

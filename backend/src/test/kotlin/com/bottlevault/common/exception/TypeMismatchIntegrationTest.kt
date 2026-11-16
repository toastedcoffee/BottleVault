// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.exception

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** A value that does not convert is a 400 that names the parameter, never internal classes. */
@SpringBootTest
@AutoConfigureMockMvc
class TypeMismatchIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val bearer: String by lazy {
        val body = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"type-mismatch-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        "Bearer " + objectMapper.readValue(body, AuthResponse::class.java).accessToken
    }

    @Test
    fun `an unknown enum value names the parameter only`() {
        mockMvc.perform(get("/api/bottles").param("type", "NOT_A_TYPE").header("Authorization", bearer))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid value for 'type'"))
            .andExpect(content().string(not(containsString("com.bottlevault"))))
    }

    @Test
    fun `a malformed id names the parameter only`() {
        mockMvc.perform(get("/api/bottles/not-a-uuid").header("Authorization", bearer))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid value for 'id'"))
            .andExpect(content().string(not(containsString("java."))))
    }
}

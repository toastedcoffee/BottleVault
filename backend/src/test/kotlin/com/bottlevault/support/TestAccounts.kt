// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.support

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Registers a fresh account through the real endpoint and returns its access token. */
fun MockMvc.registerForToken(objectMapper: ObjectMapper, label: String = "catalog"): String {
    val result = perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    RegisterRequest("$label-${UUID.randomUUID()}@example.com", "password123", label)
                )
            )
    ).andExpect(status().isCreated).andReturn()
    return objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
}

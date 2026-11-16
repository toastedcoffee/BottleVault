// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
    "app.registration.mode=invite",
    "app.turnstile.site-key=1x00000000000000000000AA",
    "app.security.privacy-url=https://example.com/privacy",
])
@AutoConfigureMockMvc
class AuthConfigIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `the public config tells the frontend what to show, without logging in`() {
        mockMvc.perform(get("/api/auth/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.registrationMode").value("invite"))
            .andExpect(jsonPath("$.turnstileSiteKey").value("1x00000000000000000000AA"))
            .andExpect(jsonPath("$.privacyUrl").value("https://example.com/privacy"))
            .andExpect(header().string("Cache-Control", "max-age=60"))
    }
}

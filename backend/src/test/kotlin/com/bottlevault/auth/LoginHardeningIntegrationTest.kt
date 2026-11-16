// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["app.rate-limit.auth.account-failures=3", "app.rate-limit.auth.account-window=15m"])
@AutoConfigureMockMvc
class LoginHardeningIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    private fun register(email: String) = mockMvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"password123"}""")
    ).andExpect(status().isCreated)

    private fun login(email: String, password: String) = mockMvc.perform(
        post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}""")
    )

    @Test
    fun `repeated failures on one account trigger a cool-down, even with the right password`() {
        val email = "throttled-${System.nanoTime()}@example.com"
        register(email)
        repeat(3) { login(email, "wrong-password").andExpect(status().isBadRequest) }
        login(email, "password123")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `the throttle follows the account, whatever case the email is typed in`() {
        val email = "throttled-case-${System.nanoTime()}@example.com"
        register(email)
        login(email.uppercase(), "wrong-password").andExpect(status().isBadRequest)
        login(" $email", "wrong-password").andExpect(status().isBadRequest)
        login(email, "wrong-password").andExpect(status().isBadRequest)
        login(email, "password123").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `a successful sign-in is unaffected by other accounts' failures`() {
        val victim = "victim-${System.nanoTime()}@example.com"
        val other = "other-${System.nanoTime()}@example.com"
        register(victim)
        repeat(3) { login(other, "wrong-password") }
        login(victim, "password123").andExpect(status().isOk)
    }

    @Test
    fun `unknown and known emails fail the same way`() {
        val known = "known-${System.nanoTime()}@example.com"
        register(known)
        login(known, "wrong-password")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
        login("nobody-${System.nanoTime()}@example.com", "password123")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
    }
}

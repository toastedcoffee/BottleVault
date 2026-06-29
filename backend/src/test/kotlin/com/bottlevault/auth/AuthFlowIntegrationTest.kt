package com.bottlevault.auth

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotEquals

/**
 * End-to-end tests for the refresh token lifecycle: rotation (single-use),
 * logout revocation, and revoke-all on password change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val password = "password123"
    private lateinit var auth: AuthResponse

    @BeforeEach
    fun registerFreshUser() {
        val request = RegisterRequest(
            email = "authflow-${System.nanoTime()}@example.com",
            password = password,
            displayName = "Auth Flow Test"
        )
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        auth = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
    }

    @Test
    fun `refresh rotates the token and the old one is rejected`() {
        val refreshed = refresh(auth.refreshToken).andExpect(status().isOk).andReturn()
        val newAuth = objectMapper.readValue(refreshed.response.contentAsString, AuthResponse::class.java)

        assertNotEquals(auth.refreshToken, newAuth.refreshToken, "Refresh must issue a new token")

        // The consumed token must not work a second time (single-use).
        refresh(auth.refreshToken).andExpect(status().isBadRequest)

        // The rotated tokens are fully usable.
        refresh(newAuth.refreshToken).andExpect(status().isOk)
    }

    @Test
    fun `refreshed access token authenticates API requests`() {
        val refreshed = refresh(auth.refreshToken).andExpect(status().isOk).andReturn()
        val newAuth = objectMapper.readValue(refreshed.response.contentAsString, AuthResponse::class.java)

        mockMvc.perform(
            get("/api/bottles").header("Authorization", "Bearer ${newAuth.accessToken}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `logout revokes the refresh token`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "${auth.refreshToken}"}""")
        ).andExpect(status().isNoContent)

        refresh(auth.refreshToken).andExpect(status().isBadRequest)
    }

    @Test
    fun `logout with an unknown token is an idempotent no-op`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "not-a-real-token"}""")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `password change revokes all refresh sessions`() {
        // Log in on a "second device" so the user has two active sessions.
        val secondLogin = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "${auth.user.email}", "password": "$password"}""")
        ).andExpect(status().isOk).andReturn()
        val secondAuth = objectMapper.readValue(secondLogin.response.contentAsString, AuthResponse::class.java)

        mockMvc.perform(
            put("/api/user/password")
                .header("Authorization", "Bearer ${auth.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "$password", "newPassword": "newPassword456"}""")
        ).andExpect(status().isNoContent)

        // Both sessions' refresh tokens are dead.
        refresh(auth.refreshToken).andExpect(status().isBadRequest)
        refresh(secondAuth.refreshToken).andExpect(status().isBadRequest)
    }

    @Test
    fun `garbage refresh token is rejected`() {
        refresh("definitely-not-issued-by-us").andExpect(status().isBadRequest)
    }

    @Test
    fun `access token is not accepted as a refresh token`() {
        refresh(auth.accessToken).andExpect(status().isBadRequest)
    }

    private fun refresh(token: String) = mockMvc.perform(
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"refreshToken": "$token"}""")
    )
}

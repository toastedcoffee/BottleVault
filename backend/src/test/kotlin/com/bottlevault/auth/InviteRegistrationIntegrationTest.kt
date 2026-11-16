// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@SpringBootTest(properties = ["app.registration.mode=invite"])
@AutoConfigureMockMvc
class InviteRegistrationIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var invites: InviteCodeRepository
    @Autowired lateinit var users: UserRepository

    /** Stores an invite and returns its displayable code. */
    private fun invite(
        email: String? = null,
        maxUses: Int = 1,
        expiresAt: Instant = Instant.now().plus(14, ChronoUnit.DAYS),
        revoked: Boolean = false,
    ): String {
        val body = (1..InviteCodes.LENGTH).map { InviteCodes.ALPHABET.random() }.joinToString("")
        invites.save(InviteCode(
            codeHash = InviteCodes.hash(body), email = email, maxUses = maxUses,
            expiresAt = expiresAt, revokedAt = if (revoked) Instant.now() else null,
        ))
        return body.chunked(4).joinToString("-")
    }

    private fun register(email: String, code: String?) = mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"password123"${code?.let { ",\"inviteCode\":\"$it\"" } ?: ""}}""")
    )

    private fun uniqueEmail(tag: String) = "$tag-${System.nanoTime()}@example.com"

    @Test
    fun `a valid code creates the account and records the invite`() {
        val email = uniqueEmail("invited")
        register(email, invite()).andExpect(status().isCreated)
        assertNotNull(users.findByEmail(email)!!.inviteCodeId)
    }

    @Test
    fun `no code, or a wrong one, is refused with one generic message`() {
        register(uniqueEmail("nocode"), null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid or expired invite code"))
        register(uniqueEmail("wrong"), "ZZZZ-ZZZZ-ZZZZ-ZZZZ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid or expired invite code"))
    }

    @Test
    fun `a single-use code works once`() {
        val code = invite()
        register(uniqueEmail("first"), code).andExpect(status().isCreated)
        register(uniqueEmail("second"), code).andExpect(status().isBadRequest)
    }

    @Test
    fun `a multi-use code works up to its limit`() {
        val code = invite(maxUses = 2)
        register(uniqueEmail("multi-1"), code).andExpect(status().isCreated)
        register(uniqueEmail("multi-2"), code).andExpect(status().isCreated)
        register(uniqueEmail("multi-3"), code).andExpect(status().isBadRequest)
    }

    @Test
    fun `expired and revoked codes are refused`() {
        register(uniqueEmail("expired"), invite(expiresAt = Instant.now().minusSeconds(60))).andExpect(status().isBadRequest)
        register(uniqueEmail("revoked"), invite(revoked = true)).andExpect(status().isBadRequest)
    }

    @Test
    fun `an email-bound code only works for that email, in any case`() {
        val email = uniqueEmail("bound")
        val code = invite(email = email)
        register(uniqueEmail("someone-else"), code).andExpect(status().isBadRequest)
        register(email.uppercase(), code).andExpect(status().isCreated)
    }

    @Test
    fun `a failed registration does not burn the code`() {
        val existing = uniqueEmail("existing")
        register(existing, invite()).andExpect(status().isCreated)
        val code = invite()
        register(existing, code).andExpect(status().isConflict)
        register(uniqueEmail("after-conflict"), code).andExpect(status().isCreated)
    }

    @Test
    fun `without a valid code the endpoint reveals nothing about existing emails`() {
        val existing = uniqueEmail("taken")
        register(existing, invite()).andExpect(status().isCreated)
        register(existing, "ZZZZ-ZZZZ-ZZZZ-ZZZZ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid or expired invite code"))
    }

    @Test
    fun `two people racing for one single-use code - exactly one wins`() {
        val code = invite()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(uniqueEmail("race-a"), uniqueEmail("race-b"))
                .map { email -> Callable { register(email, code).andReturn().response.status } }
                .let { pool.invokeAll(it) }
                .map { it.get() }
            assertEquals(listOf(201, 400), results.sorted())
        } finally {
            pool.shutdown()
        }
    }
}

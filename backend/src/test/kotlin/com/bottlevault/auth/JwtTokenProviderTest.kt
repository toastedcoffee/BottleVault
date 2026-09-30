// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import io.jsonwebtoken.security.WeakKeyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Plain unit tests: no Spring context and no Docker. */
class JwtTokenProviderTest {

    @Test
    fun `a secret shorter than 256 bits fails at construction, not at the first login`() {
        // 31 bytes: one short of HS256's floor.
        assertThrows<WeakKeyException> { JwtTokenProvider("x".repeat(31), 60_000) }
    }

    @Test
    fun `a 256-bit secret issues tokens that validate`() {
        val provider = JwtTokenProvider("x".repeat(32), 60_000)
        val token = provider.generateAccessToken("0b7c5a44-6f7e-4c3e-9d1a-2f0e8a1b3c4d")
        assertTrue(provider.validateToken(token))
        assertEquals("0b7c5a44-6f7e-4c3e-9d1a-2f0e8a1b3c4d", provider.getUserIdFromToken(token))
        assertEquals("access", provider.getTokenType(token))
    }
}

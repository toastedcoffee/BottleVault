// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** The vectors are a contract with scripts/ops/match-ip.sh — change both or neither. */
class IdentifierHasherTest {
    private val hasher = IdentifierHasher("test-ip-hash-key")

    @Test
    fun `IP hashes match the contract vectors`() {
        assertEquals("1da31e563f8f6fb1", hasher.hash("203.0.113.7"))
        assertEquals("28293a83c39f72d1", hasher.hash("2001:db8:1:2::/64"))
        assertEquals("3a1c7a2a26e84cda", hasher.hash("2001:db8:0:2::/64"))
    }

    @Test
    fun `emails are normalized before hashing`() {
        assertEquals("8a323ffef54de61e", hasher.hashEmail(" User@Example.COM "))
        assertEquals(hasher.hashEmail("user@example.com"), hasher.hashEmail("USER@example.com"))
    }

    @Test
    fun `without a configured key the hashes still work but differ from the keyed ones`() {
        val unkeyed = IdentifierHasher("")
        assertEquals(16, unkeyed.hash("203.0.113.7").length)
        assertNotEquals("1da31e563f8f6fb1", unkeyed.hash("203.0.113.7"))
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RegistrationModeTest {
    @Test
    fun `an explicit mode wins, in any case`() {
        assertEquals(RegistrationMode.INVITE, RegistrationMode.resolve("invite", legacyEnabled = true))
        assertEquals(RegistrationMode.CLOSED, RegistrationMode.resolve(" Closed ", legacyEnabled = true))
        assertEquals(RegistrationMode.OPEN, RegistrationMode.resolve("OPEN", legacyEnabled = false))
    }

    @Test
    fun `an empty mode falls back to the legacy switch`() {
        assertEquals(RegistrationMode.OPEN, RegistrationMode.resolve("", legacyEnabled = true))
        assertEquals(RegistrationMode.CLOSED, RegistrationMode.resolve("  ", legacyEnabled = false))
    }

    @Test
    fun `a typo stops the app instead of silently opening registration`() {
        assertThrows(IllegalStateException::class.java) { RegistrationMode.resolve("invites", legacyEnabled = true) }
    }
}

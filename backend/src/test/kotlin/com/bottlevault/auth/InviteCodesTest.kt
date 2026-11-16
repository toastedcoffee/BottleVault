// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InviteCodesTest {
    @Test
    fun `accepts the displayed form, lower case, spaces, and look-alike letters`() {
        assertEquals("7K3M9Q2XH4TR8WPA", InviteCodes.normalize("7K3M-9Q2X-H4TR-8WPA"))
        assertEquals("7K3M9Q2XH4TR8WPA", InviteCodes.normalize(" 7k3m 9q2x h4tr 8wpa "))
        assertEquals("0000111100001111", InviteCodes.normalize("OOOO-IIII-oooo-LLLL"))
    }

    @Test
    fun `rejects anything that cannot be a code`() {
        listOf(null, "", "7K3M-9Q2X-H4TR", "7K3M-9Q2X-H4TR-8WPAX", "7K3M-9Q2X-H4TR-8WPU", "7K3M-9Q2X-H4TR-8WP!")
            .forEach { assertNull(InviteCodes.normalize(it), "should reject: $it") }
    }

    @Test
    fun `hash is the SHA-256 hex of the normalized code`() {
        // printf '%s' 7K3M9Q2XH4TR8WPA | sha256sum
        assertEquals(64, InviteCodes.hash("7K3M9Q2XH4TR8WPA").length)
        assertEquals(InviteCodes.hash("7K3M9Q2XH4TR8WPA"), InviteCodes.hash(InviteCodes.normalize("7k3m-9q2x-h4tr-8wpa")!!))
    }
}

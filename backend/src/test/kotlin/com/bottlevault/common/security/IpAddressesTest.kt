// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IpAddressesTest {

    private fun canonical(raw: String) = IpAddresses.canonical(IpAddresses.parse(raw)!!)

    @Test
    fun `canonical forms match the contract vectors`() {
        assertEquals("203.0.113.7", canonical("203.0.113.7"))
        assertEquals("203.0.113.7", canonical("::ffff:203.0.113.7"))
        assertEquals("2001:db8:1:2::/64", canonical("2001:db8:1:2:3:4:5:6"))
        assertEquals("2001:db8:0:2::/64", canonical("2001:0DB8:0000:0002::1"))
        assertEquals("2001:db8:1:2::/64", canonical("2001:db8:1:2:ffff::9"))
    }

    @Test
    fun `anything that is not an IP literal is rejected without a DNS lookup`() {
        listOf(null, "", " ", "example.com", "localhost", "1.2.3", "1.2.3.4.5", "256.1.1.1",
            "01.2.3.4", "fe80::1%eth0", "1.2.3.4, 5.6.7.8", "x".repeat(60)).forEach {
            assertNull(IpAddresses.parse(it), "should reject: $it")
        }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals("203.0.113.7", canonical("  203.0.113.7 "))
    }
}

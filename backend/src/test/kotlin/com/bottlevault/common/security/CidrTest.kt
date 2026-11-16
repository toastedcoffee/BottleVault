// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CidrTest {
    private fun ip(s: String) = IpAddresses.parse(s)!!

    @Test
    fun `a single address`() {
        val cidr = Cidr.parse("172.29.252.10/32")
        assertTrue(cidr.contains(ip("172.29.252.10")))
        assertFalse(cidr.contains(ip("172.29.252.11")))
    }

    @Test
    fun `a subnet, including a prefix that is not a multiple of eight`() {
        val cidr = Cidr.parse("10.9.8.0/22")
        assertTrue(cidr.contains(ip("10.9.11.255")))
        assertFalse(cidr.contains(ip("10.9.12.0")))
    }

    @Test
    fun `address families never match each other`() {
        assertFalse(Cidr.parse("0.0.0.0/0").contains(ip("2001:db8::1")))
        assertTrue(Cidr.parse("2001:db8::/32").contains(ip("2001:db8:ffff::1")))
    }

    @Test
    fun `a bare address means the whole address`() {
        assertTrue(Cidr.parse("192.0.2.1").contains(ip("192.0.2.1")))
        assertFalse(Cidr.parse("192.0.2.1").contains(ip("192.0.2.2")))
    }

    @Test
    fun `garbage fails loudly at startup rather than trusting nobody silently`() {
        assertThrows(IllegalArgumentException::class.java) { Cidr.parse("not-an-ip/8") }
        assertThrows(IllegalArgumentException::class.java) { Cidr.parse("10.0.0.0/33") }
    }
}

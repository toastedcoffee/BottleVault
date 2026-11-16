// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import java.net.InetAddress

/** An address block such as 172.29.252.10/32 or 2001:db8::/32. */
class Cidr private constructor(private val network: ByteArray, private val prefix: Int) {

    fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != network.size) return false
        var remaining = prefix
        for (i in bytes.indices) {
            if (remaining <= 0) return true
            val mask = if (remaining >= 8) 0xff else (0xff shl (8 - remaining)) and 0xff
            if ((bytes[i].toInt() and mask) != (network[i].toInt() and mask)) return false
            remaining -= 8
        }
        return true
    }

    companion object {
        fun parse(text: String): Cidr {
            val parts = text.trim().split('/', limit = 2)
            val address = IpAddresses.parse(parts[0])
                ?: throw IllegalArgumentException("Not an IP address in CIDR '$text'")
            val max = address.address.size * 8
            val prefix = if (parts.size == 2) parts[1].toIntOrNull() else max
            require(prefix != null && prefix in 0..max) { "Bad prefix length in CIDR '$text'" }
            return Cidr(address.address, prefix)
        }
    }
}

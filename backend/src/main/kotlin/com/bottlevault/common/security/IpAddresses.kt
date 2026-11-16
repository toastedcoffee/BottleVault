// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Client IP addresses as the security layer sees them.
 *
 * The canonical form is what gets rate-limited and hashed, and it is a contract:
 * scripts/ops/match-ip.sh reproduces it byte for byte (vectors in
 * IdentifierHasherTest). IPv4: dotted decimal. IPv4-mapped IPv6: its IPv4 form.
 * Other IPv6: the /64 prefix — the first four groups in lowercase hex without
 * leading zeros, then "::/64" — because one subscriber holds a whole /64 and
 * moving around inside it is free.
 */
object IpAddresses {
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val IPV6 = Regex("""^[0-9A-Fa-f:.]+$""")

    /** Parses an IP literal and never does a DNS lookup; null when [value] is not one. */
    fun parse(value: String?): InetAddress? {
        val s = value?.trim().orEmpty()
        if (s.isEmpty() || s.length > 45) return null
        val isLiteral = when {
            IPV4.matches(s) -> s.split('.').all { part -> part.toInt() <= 255 && (part == "0" || !part.startsWith("0")) }
            s.contains(':') -> IPV6.matches(s)
            else -> false
        }
        if (!isLiteral) return null
        return runCatching { InetAddress.getByName(s) }.getOrNull()
    }

    fun canonical(address: InetAddress): String = when (address) {
        is Inet4Address -> address.hostAddress
        is Inet6Address -> {
            val b = address.address
            (0 until 4).joinToString(":") { i ->
                Integer.toHexString(((b[2 * i].toInt() and 0xff) shl 8) or (b[2 * i + 1].toInt() and 0xff))
            } + "::/64"
        }
        else -> address.hostAddress
    }
}

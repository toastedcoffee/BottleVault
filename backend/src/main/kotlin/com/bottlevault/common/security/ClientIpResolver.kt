// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetAddress

/** Who the client is, as far as rate limits and the security log are concerned. */
data class ClientIp(
    /** Raw address; used in memory and for Turnstile's remoteip, never written to a log. */
    val address: String,
    /** Canonical form: IPv4 as-is, IPv6 as its /64. */
    val rateLimitKey: String,
    /** Keyed hash of the canonical form, for the security log. */
    val hash: String,
)

/**
 * Decides the client's address (spec contract C2).
 *
 * nginx always sets X-BV-Peer-IP to whoever connected to it. Only when that peer
 * is inside TRUSTED_PROXY_CIDRS — the tunnel container — is CF-Connecting-IP
 * believed. X-Real-IP and X-Forwarded-For are ignored: anyone can send them.
 */
@Component
class ClientIpResolver(
    private val hasher: IdentifierHasher,
    @Value("\${app.security.trusted-proxy-cidrs:}") trustedCsv: String,
) {
    private val trusted: List<Cidr> = trustedCsv.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { Cidr.parse(it) }

    fun resolve(request: HttpServletRequest): ClientIp {
        val peer = IpAddresses.parse(request.getHeader(PEER_HEADER))
            ?: IpAddresses.parse(request.remoteAddr)
            ?: InetAddress.getLoopbackAddress()
        val vouched = IpAddresses.parse(request.getHeader(CF_HEADER))
        val client = if (vouched != null && trusted.any { it.contains(peer) }) vouched else peer
        val canonical = IpAddresses.canonical(client)
        return ClientIp(address = client.hostAddress, rateLimitKey = canonical, hash = hasher.hash(canonical))
    }

    companion object {
        const val PEER_HEADER = "X-BV-Peer-IP"
        const val CF_HEADER = "CF-Connecting-IP"
    }
}

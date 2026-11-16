// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {
    private val hasher = IdentifierHasher("test-ip-hash-key")
    private val resolver = ClientIpResolver(hasher, "172.29.252.10/32")

    private fun request(remote: String = "127.0.0.1", peer: String? = null, cf: String? = null) =
        MockHttpServletRequest().apply {
            remoteAddr = remote
            peer?.let { addHeader(ClientIpResolver.PEER_HEADER, it) }
            cf?.let { addHeader(ClientIpResolver.CF_HEADER, it) }
        }

    @Test
    fun `the tunnel may vouch for the visitor`() {
        val ip = resolver.resolve(request(remote = "172.30.0.5", peer = "172.29.252.10", cf = "203.0.113.7"))
        assertEquals("203.0.113.7", ip.address)
        assertEquals("203.0.113.7", ip.rateLimitKey)
        assertEquals("1da31e563f8f6fb1", ip.hash)
    }

    @Test
    fun `anyone else claiming a visitor address is ignored`() {
        val ip = resolver.resolve(request(remote = "172.30.0.5", peer = "192.168.1.50", cf = "203.0.113.7"))
        assertEquals("192.168.1.50", ip.address)
    }

    @Test
    fun `without nginx the socket address is the peer`() {
        assertEquals("127.0.0.1", resolver.resolve(request(cf = "203.0.113.7")).address)
    }

    @Test
    fun `X-Real-IP and X-Forwarded-For are never believed`() {
        val req = request(remote = "10.1.1.1").apply {
            addHeader("X-Real-IP", "203.0.113.9")
            addHeader("X-Forwarded-For", "203.0.113.9")
        }
        assertEquals("10.1.1.1", resolver.resolve(req).address)
    }

    @Test
    fun `a malformed forwarded header falls back to the peer`() {
        val ip = resolver.resolve(request(peer = "172.29.252.10", cf = "evil.example.com"))
        assertEquals("172.29.252.10", ip.address)
    }

    @Test
    fun `IPv6 clients are keyed by their slash-64`() {
        val a = resolver.resolve(request(peer = "172.29.252.10", cf = "2001:db8:1:2:3:4:5:6"))
        val b = resolver.resolve(request(peer = "172.29.252.10", cf = "2001:db8:1:2:ffff::9"))
        assertEquals("2001:db8:1:2::/64", a.rateLimitKey)
        assertEquals(a.rateLimitKey, b.rateLimitKey)
        assertEquals("28293a83c39f72d1", a.hash)
    }

    @Test
    fun `an empty trust list trusts nobody`() {
        val open = ClientIpResolver(hasher, "")
        assertEquals("172.29.252.10", open.resolve(request(peer = "172.29.252.10", cf = "203.0.113.7")).address)
    }
}

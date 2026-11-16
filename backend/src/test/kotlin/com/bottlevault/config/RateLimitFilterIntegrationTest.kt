// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bottlevault.common.security.ClientIpResolver
import com.bottlevault.common.security.SecurityEvents
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
    "app.rate-limit.max-attempts=3",
    "app.rate-limit.auth.register-limit=2",
    "app.security.trusted-proxy-cidrs=10.9.9.9/32",
])
@AutoConfigureMockMvc
class RateLimitFilterIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun login(peer: String?, cf: String?) = mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"nobody@example.com","password":"wrong-password"}""")
            .apply {
                peer?.let { header(ClientIpResolver.PEER_HEADER, it) }
                cf?.let { header(ClientIpResolver.CF_HEADER, it) }
            }
    )

    private fun register(peer: String, n: Int) = mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"rl-register-$n@example.com","password":"password123","displayName":"RL $n"}""")
            .header(ClientIpResolver.PEER_HEADER, peer)
    )

    @Test
    fun `a spoofed CF-Connecting-IP from an untrusted peer does not buy extra attempts`() {
        repeat(3) { i -> login(peer = "192.168.7.7", cf = "198.51.100.$i").andExpect(status().isBadRequest) }
        login(peer = "192.168.7.7", cf = "198.51.100.99")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `X-Forwarded-For and X-Real-IP do not buy extra attempts either`() {
        repeat(3) { i ->
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"nobody@example.com","password":"wrong-password"}""")
                    .header(ClientIpResolver.PEER_HEADER, "192.168.8.8")
                    .header("X-Forwarded-For", "198.51.100.$i")
                    .header("X-Real-IP", "198.51.100.$i")
            ).andExpect(status().isBadRequest)
        }
        login(peer = "192.168.8.8", cf = null).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `a client hammering a closed bucket is logged once per window, not once per request`() {
        val captured = ListAppender<ILoggingEvent>().also { it.start() }
        val logger = LoggerFactory.getLogger(SecurityEvents.LOGGER_NAME) as Logger
        logger.addAppender(captured)
        try {
            repeat(3) { login(peer = "192.168.6.6", cf = null).andExpect(status().isBadRequest) }
            repeat(5) { login(peer = "192.168.6.6", cf = null).andExpect(status().isTooManyRequests) }
            assertEquals(1, captured.list.count { it.formattedMessage.contains("\"ratelimit.tripped\"") })
        } finally {
            logger.detachAppender(captured)
        }
    }

    @Test
    fun `behind the trusted tunnel each visitor has their own budget`() {
        repeat(3) { i -> login(peer = "10.9.9.9", cf = "203.0.113.$i").andExpect(status().isBadRequest) }
        login(peer = "10.9.9.9", cf = "203.0.113.200").andExpect(status().isBadRequest)
        repeat(2) { login(peer = "10.9.9.9", cf = "203.0.113.50").andExpect(status().isBadRequest) }
        login(peer = "10.9.9.9", cf = "203.0.113.50").andExpect(status().isBadRequest)
        login(peer = "10.9.9.9", cf = "203.0.113.50").andExpect(status().isTooManyRequests)
    }

    @Test
    fun `account creation has its own, stricter budget`() {
        register(peer = "192.168.9.9", n = 1).andExpect(status().isCreated)
        register(peer = "192.168.9.9", n = 2).andExpect(status().isCreated)
        register(peer = "192.168.9.9", n = 3)
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }
}

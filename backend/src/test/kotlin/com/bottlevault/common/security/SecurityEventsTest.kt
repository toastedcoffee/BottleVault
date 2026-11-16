// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SecurityEventsTest {
    private val hasher = IdentifierHasher("test-ip-hash-key")
    private val resolver = ClientIpResolver(hasher, "172.29.252.10/32")
    private val clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC)
    private val mapper = JsonMapper.builder().build()
    private val captured = ListAppender<ILoggingEvent>().also { it.start() }
    private val logger = (LoggerFactory.getLogger(SecurityEvents.LOGGER_NAME) as Logger).also { it.addAppender(captured) }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(captured)
        RequestContextHolder.resetRequestAttributes()
        SecurityLogFile.detach()
        ApplicationLogFile.detach()
    }

    private fun lastEvent() = mapper.readTree(captured.list.last().formattedMessage)

    @Test
    fun `an event carries hashes, never the raw address or email`() {
        val request = MockHttpServletRequest().apply {
            addHeader(ClientIpResolver.PEER_HEADER, "172.29.252.10")
            addHeader(ClientIpResolver.CF_HEADER, "203.0.113.7")
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        val userId = UUID.fromString("00000000-0000-0000-0000-000000000042")

        SecurityEvents(resolver, hasher, clock, mapper, "")
            .emit("auth.login.failure", userId = userId, email = "User@Example.com", attrs = mapOf("reason" to "bad_credentials"))

        val event = lastEvent()
        assertEquals("2026-09-22T12:00:00Z", event["ts"].asString())
        assertEquals("auth.login.failure", event["type"].asString())
        assertEquals("1da31e563f8f6fb1", event["ipHash"].asString())
        assertEquals("8a323ffef54de61e", event["emailHash"].asString())
        assertEquals(userId.toString(), event["userId"].asString())
        assertEquals("bad_credentials", event["attrs"]["reason"].asString())
        val raw = captured.list.last().formattedMessage
        assertFalse(raw.contains("203.0.113.7"))
        assertFalse(raw.contains("example.com", ignoreCase = true))
    }

    @Test
    fun `outside a request there is simply no ipHash`() {
        SecurityEvents(resolver, hasher, clock, mapper, "").emit("quota.exceeded", attrs = mapOf("kind" to "bottles"))
        assertFalse(lastEvent().has("ipHash"))
    }

    @Test
    fun `with a log directory, events also land in security log on disk`(@TempDir dir: Path) {
        SecurityEvents(resolver, hasher, clock, mapper, dir.toString()).emit("auth.register.success")
        val file = dir.resolve("security.log")
        assertTrue(Files.exists(file))
        assertTrue(Files.readString(file).contains("\"type\":\"auth.register.success\""))
    }

    @Test
    fun `with a log directory, the general application log is kept in app log too`(@TempDir dir: Path) {
        SecurityEvents(resolver, hasher, clock, mapper, dir.toString())
        LoggerFactory.getLogger("com.bottlevault.test").warn("general log line for the file")
        val file = dir.resolve("app.log")
        assertTrue(Files.exists(file))
        assertTrue(Files.readString(file).contains("general log line for the file"))
    }
}

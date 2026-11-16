// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RateLimiterTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }

    private val clock = MutableClock(Instant.parse("2026-09-22T12:00:00Z"))
    private val limiter = RateLimiter(clock)
    private val minute = Duration.ofMinutes(1)

    @Test
    fun `allows up to the limit, then refuses with a retry time`() {
        repeat(3) { assertTrue(limiter.tryAcquire("b", "k", 3, minute).allowed) }
        val refused = limiter.tryAcquire("b", "k", 3, minute)
        assertFalse(refused.allowed)
        assertEquals(60, refused.retryAfterSeconds)
    }

    @Test
    fun `a new window starts after the old one ends`() {
        repeat(3) { limiter.tryAcquire("b", "k", 3, minute) }
        clock.now = clock.now.plusSeconds(61)
        assertTrue(limiter.tryAcquire("b", "k", 3, minute).allowed)
    }

    @Test
    fun `buckets and keys are independent`() {
        repeat(3) { limiter.tryAcquire("b", "k", 3, minute) }
        assertTrue(limiter.tryAcquire("b", "other", 3, minute).allowed)
        assertTrue(limiter.tryAcquire("other", "k", 3, minute).allowed)
    }

    @Test
    fun `check peeks without counting, record counts without deciding`() {
        repeat(5) { assertTrue(limiter.check("fail", "acct", 2, minute).allowed) }
        limiter.record("fail", "acct", minute)
        assertTrue(limiter.check("fail", "acct", 2, minute).allowed)
        limiter.record("fail", "acct", minute)
        assertFalse(limiter.check("fail", "acct", 2, minute).allowed)
    }

    @Test
    fun `retry time never reads as zero`() {
        repeat(3) { limiter.tryAcquire("b", "k", 3, minute) }
        clock.now = clock.now.plusMillis(59_900)
        assertEquals(1, limiter.tryAcquire("b", "k", 3, minute).retryAfterSeconds)
    }
}

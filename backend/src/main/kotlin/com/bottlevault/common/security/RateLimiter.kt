// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class RateLimitDecision(val allowed: Boolean, val retryAfterSeconds: Long) {
    companion object {
        val ALLOWED = RateLimitDecision(true, 0)
    }
}

/**
 * Fixed-window counters, in memory, one per (bucket, key).
 *
 * The map is size-bounded and entries expire, so a flood of distinct keys costs
 * accuracy (a window may reset early), never memory. Counters reset when the
 * API restarts; that is accepted for a single-instance deployment.
 *
 * Shared by S4's per-user limits and the catalog moderation work: the names and
 * semantics of tryAcquire, check, record and RateLimitDecision are frozen.
 * Change it only additively.
 */
@Component
class RateLimiter(private val clock: Clock) {

    private class Window(var start: Instant, var count: Int)

    private val windows: Cache<String, Window> = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofHours(25))
        .build()

    /** Counts this attempt; allowed while fewer than [limit] were counted in the current window. */
    fun tryAcquire(bucket: String, key: String, limit: Int, window: Duration): RateLimitDecision =
        update(bucket, key, window) { w -> if (w.count < limit) { w.count++; false } else true }

    /** Whether [limit] is already used up. Counts nothing. */
    fun check(bucket: String, key: String, limit: Int, window: Duration): RateLimitDecision =
        update(bucket, key, window) { w -> w.count >= limit }

    /** Counts one event without deciding anything — e.g. a failed sign-in. */
    fun record(bucket: String, key: String, window: Duration) {
        update(bucket, key, window) { w -> w.count++; false }
    }

    private fun update(bucket: String, key: String, window: Duration, limited: (Window) -> Boolean): RateLimitDecision {
        val now = clock.instant()
        val w = windows.get("$bucket|$key") { Window(now, 0) }
        synchronized(w) {
            if (!now.isBefore(w.start.plus(window))) {
                w.start = now
                w.count = 0
            }
            if (!limited(w)) return RateLimitDecision.ALLOWED
            val retry = Duration.between(now, w.start.plus(window))
            return RateLimitDecision(false, maxOf(1, (retry.toMillis() + 999) / 1000))
        }
    }
}

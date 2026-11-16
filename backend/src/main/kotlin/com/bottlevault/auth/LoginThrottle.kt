// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.common.exception.RateLimitedException
import com.bottlevault.common.security.IdentifierHasher
import com.bottlevault.common.security.RateLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Failed sign-ins per account. Keyed by the account's email hash, so it holds
 * however many addresses an attacker rotates through. Trade-off, accepted:
 * anyone can lock a known email out for one window; Turnstile makes that costly.
 */
@Component
class LoginThrottle(
    private val rateLimiter: RateLimiter,
    private val hasher: IdentifierHasher,
    @Value("\${app.rate-limit.auth.account-failures:10}") private val maxFailures: Int,
    @Value("\${app.rate-limit.auth.account-window:15m}") private val window: Duration,
) {
    fun ensureAllowed(normalizedEmail: String) {
        val decision = rateLimiter.check(BUCKET, hasher.hashEmail(normalizedEmail), maxFailures, window)
        if (!decision.allowed) {
            throw RateLimitedException("Too many failed sign-in attempts for this account. Try again later.", decision.retryAfterSeconds)
        }
    }

    fun recordFailure(normalizedEmail: String) = rateLimiter.record(BUCKET, hasher.hashEmail(normalizedEmail), window)

    private companion object {
        const val BUCKET = "login-account"
    }
}

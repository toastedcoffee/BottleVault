// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Whether an account may use its access tokens. Access tokens are stateless and
 * live 15 minutes; this cache (one lookup per user per minute) is what makes a
 * suspension or deletion bite within a minute instead.
 */
@Component
class UserStatusCache(private val users: UserRepository) {

    private val active: Cache<UUID, Boolean> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(60))
        .build()

    /** False for a suspended or deleted account. */
    fun isActive(userId: UUID): Boolean =
        active.get(userId) { id -> users.findById(id).map { it.disabledAt == null }.orElse(false) }

    fun forget(userId: UUID) = active.invalidate(userId)
}

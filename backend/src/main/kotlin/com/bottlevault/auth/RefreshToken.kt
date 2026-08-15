// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * One active refresh session for a user. Stores only the SHA-256 hash of the
 * opaque token — the raw value exists nowhere but the client. A row being
 * present and unexpired is what makes a refresh token valid; deleting the
 * row revokes it.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)

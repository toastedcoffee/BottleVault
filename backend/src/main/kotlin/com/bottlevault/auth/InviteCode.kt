// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invite_codes")
class InviteCode(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    val codeHash: String,

    /** When set, only this (normalized) email may use the code. */
    @Column(length = 255)
    val email: String? = null,

    @Column(length = 200)
    val note: String? = null,

    @Column(name = "max_uses", nullable = false)
    val maxUses: Int = 1,

    @Column(name = "use_count", nullable = false)
    var useCount: Int = 0,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

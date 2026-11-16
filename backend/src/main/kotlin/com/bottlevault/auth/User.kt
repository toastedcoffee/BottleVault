// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "default_currency")
    var defaultCurrency: String = "USD",

    @Column(name = "measurement_unit")
    var measurementUnit: String = "ml",

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),

    /** The invite that created this account; null for accounts made without one. */
    @Column(name = "invite_code_id")
    var inviteCodeId: UUID? = null,

    /** Set by scripts/suspend-user.sh; a suspended account cannot sign in. */
    @Column(name = "disabled_at")
    var disabledAt: Instant? = null,

    @Column(name = "disabled_reason", length = 200)
    var disabledReason: String? = null,
)

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "brands")
class Brand(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    /** What a human sees. Trimmed and whitespace-collapsed; case and punctuation preserved. */
    @Column(name = "display_name", nullable = false)
    var displayName: String,

    /** Matching key. Unique. Always derived via NameNormalizer, never set by hand. */
    @Column(name = "normalized_name", nullable = false, unique = true)
    var normalizedName: String,

    /** Extra search terms, e.g. "Suntory" against Hibiki. A search affordance, not a claim of ownership. */
    var aliases: String? = null,

    @Column(name = "normalized_aliases")
    var normalizedAliases: String? = null,

    var country: String? = null,

    var website: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

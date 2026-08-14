// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun deleteByTokenHash(tokenHash: String)
    fun deleteAllByUserId(userId: UUID)
    fun deleteByExpiresAtBefore(cutoff: Instant)

    /**
     * Single-statement conditional delete. Returns the number of rows removed
     * (0 or 1), letting the caller detect that a concurrent request already
     * consumed the row — entity-level delete() is a silent no-op in that case.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.id = :id")
    fun deleteRowById(id: UUID): Int
}

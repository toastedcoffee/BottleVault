// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface InviteCodeRepository : JpaRepository<InviteCode, UUID> {

    fun findByCodeHash(codeHash: String): InviteCode?

    /**
     * Uses one invite, atomically: one row updated means accepted. The row lock
     * serializes concurrent uses, so a single-use code can never create two
     * accounts, and every rejection reason (unknown, used up, expired, revoked,
     * bound to another email) looks the same to the caller.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE InviteCode i SET i.useCount = i.useCount + 1
        WHERE i.codeHash = :codeHash
          AND i.revokedAt IS NULL
          AND i.expiresAt > :now
          AND i.useCount < i.maxUses
          AND (i.email IS NULL OR i.email = :email)
        """
    )
    fun consume(codeHash: String, email: String, now: Instant): Int
}

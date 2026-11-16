// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class InviteService(private val invites: InviteCodeRepository, private val clock: Clock) {

    /**
     * Uses one invite for [normalizedEmail] and returns its id, or null when the
     * code cannot be used. MANDATORY: it must share the registration's
     * transaction, so a registration that fails later does not burn the code.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun consume(code: String?, normalizedEmail: String): UUID? {
        val normalized = InviteCodes.normalize(code) ?: return null
        val hash = InviteCodes.hash(normalized)
        if (invites.consume(hash, normalizedEmail, clock.instant()) != 1) return null
        return invites.findByCodeHash(hash)?.id
    }
}

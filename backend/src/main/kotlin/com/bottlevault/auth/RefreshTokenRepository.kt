package com.bottlevault.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun deleteByTokenHash(tokenHash: String)
    fun deleteAllByUserId(userId: UUID)
    fun deleteByExpiresAtBefore(cutoff: Instant)
}

package com.bottlevault.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Issues and validates opaque refresh tokens backed by the refresh_tokens table.
 *
 * Design choices, for the record:
 * - Tokens are 256-bit random values, not JWTs. There is nothing to decode or
 *   forge — validity is purely "does the hash exist in the database".
 * - Only the SHA-256 hash is persisted. SHA-256 (rather than BCrypt) is fine
 *   here because the input already has 256 bits of entropy; brute-forcing a
 *   hash leak is infeasible, and we need a deterministic value to look up by.
 * - Tokens are single-use: every successful refresh deletes the old row and
 *   issues a new token (rotation). A stolen-then-replayed token therefore
 *   fails as soon as the legitimate client has refreshed.
 */
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${app.jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long
) {
    private val secureRandom = SecureRandom()

    /** Creates a new refresh session for the user and returns the raw token. */
    @Transactional
    fun issue(userId: UUID): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = sha256Hex(rawToken),
                expiresAt = Instant.now().plusMillis(refreshExpirationMs)
            )
        )
        return rawToken
    }

    /**
     * Validates a token and consumes it (single-use rotation).
     * Returns the owning user's id, or null if the token is unknown or expired.
     * The row is deleted even when expired, so failed tokens can't linger.
     */
    @Transactional
    fun consume(rawToken: String): UUID? {
        val stored = refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)) ?: return null
        refreshTokenRepository.delete(stored)
        return if (stored.expiresAt.isAfter(Instant.now())) stored.userId else null
    }

    /** Revokes a single session (logout). Unknown tokens are a no-op. */
    @Transactional
    fun revoke(rawToken: String) {
        refreshTokenRepository.deleteByTokenHash(sha256Hex(rawToken))
    }

    /** Revokes every session for a user (password change, admin reset). */
    @Transactional
    fun revokeAllForUser(userId: UUID) {
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    /** Daily sweep of expired rows that were never used or revoked. */
    @Scheduled(fixedDelayString = "\${app.refresh-token.cleanup-interval-ms:86400000}")
    @Transactional
    fun purgeExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now())
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

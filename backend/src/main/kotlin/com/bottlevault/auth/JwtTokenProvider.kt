package com.bottlevault.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * Issues and parses short-lived access tokens. Refresh tokens are NOT JWTs —
 * they are opaque values managed server-side by [RefreshTokenService].
 */
@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms}") private val accessExpirationMs: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateAccessToken(userId: String): String {
        val now = Date()
        val expiry = Date(now.time + accessExpirationMs)

        return Jwts.builder()
            .subject(userId)
            // The type claim lets JwtAuthenticationFilter reject any non-access
            // token (e.g. a legacy refresh JWT) presented as a bearer token.
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun getUserIdFromToken(token: String): String =
        getClaims(token).subject

    fun getTokenType(token: String): String =
        getClaims(token)["type"] as String

    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}

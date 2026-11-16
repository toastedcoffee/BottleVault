// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.auth.dto.*
import com.bottlevault.common.exception.AccessDeniedException
import com.bottlevault.common.exception.InvalidInviteException
import com.bottlevault.common.exception.RateLimitedException
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.security.Emails
import com.bottlevault.common.security.SecurityEvents
import com.bottlevault.user.dto.ChangePasswordRequest
import com.bottlevault.user.dto.UpdateProfileRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val inviteService: InviteService,
    private val loginThrottle: LoginThrottle,
    private val securityEvents: SecurityEvents,
    @Value("\${app.registration.mode:}") registrationModeSetting: String,
    @Value("\${app.registration.enabled:true}") registrationEnabled: Boolean,
    @Value("\${app.turnstile.site-key:}") private val turnstileSiteKey: String,
    @Value("\${app.security.privacy-url:}") private val privacyUrl: String,
) {
    private val registrationMode = RegistrationMode.resolve(registrationModeSetting, registrationEnabled)

    // A real bcrypt hash of nothing in particular: comparing against it when the
    // email is unknown makes both failure paths take the same time. Built eagerly,
    // not lazily: the first unknown-email sign-in would otherwise pay for one extra
    // encode, a measurable blip in exactly the timing this hides.
    private val timingDummyHash: String = passwordEncoder.encode("timing-equalizer-not-a-password")!!

    fun publicConfig() = AuthConfigResponse(
        registrationMode = registrationMode.name.lowercase(),
        turnstileSiteKey = turnstileSiteKey.ifBlank { null },
        privacyUrl = privacyUrl.ifBlank { null },
    )

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (registrationMode == RegistrationMode.CLOSED) {
            securityEvents.emit("auth.register.failure", email = request.email, attrs = mapOf("reason" to "closed"))
            // AccessDeniedException maps to a clean 403 via GlobalExceptionHandler;
            // IllegalStateException would fall through as an unhandled 500.
            throw AccessDeniedException("Registration is currently disabled")
        }
        val email = Emails.normalize(request.email)
        PasswordRules.check(request.password)

        // The invite is checked BEFORE whether the email exists: without a valid
        // code this endpoint must not reveal which emails have accounts.
        val inviteId = if (registrationMode == RegistrationMode.INVITE) {
            inviteService.consume(request.inviteCode, email) ?: run {
                securityEvents.emit("auth.register.failure", email = email, attrs = mapOf("reason" to "invite_invalid"))
                throw InvalidInviteException()
            }
        } else {
            null
        }

        if (userRepository.existsByEmail(email)) {
            securityEvents.emit("auth.register.failure", email = email, attrs = mapOf("reason" to "email_exists"))
            throw ResourceAlreadyExistsException("An account with this email already exists")
        }

        val saved = try {
            // Flushed here so a concurrent registration of the same email, which
            // passed existsByEmail too, hits the unique constraint as a clean 409.
            userRepository.saveAndFlush(
                User(
                    email = email,
                    // Spring Security 7 annotates PasswordEncoder.encode as @Nullable; BCrypt
                    // never returns null, so assert non-null rather than propagate nullability.
                    passwordHash = passwordEncoder.encode(request.password)!!,
                    displayName = request.displayName?.trim()?.ifEmpty { null },
                    inviteCodeId = inviteId,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            securityEvents.emit("auth.register.failure", email = email, attrs = mapOf("reason" to "email_exists"))
            throw ResourceAlreadyExistsException("An account with this email already exists")
        }
        securityEvents.emit("auth.register.success", userId = saved.id, attrs = mapOf("inviteId" to inviteId?.toString()))
        return createAuthResponse(saved)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val email = Emails.normalize(request.email)
        try {
            loginThrottle.ensureAllowed(email)
        } catch (e: RateLimitedException) {
            securityEvents.emit("auth.login.failure", email = email, attrs = mapOf("reason" to "throttled"))
            throw e
        }
        val user = userRepository.findByEmail(email)
        val passwordMatches = if (user == null) {
            passwordEncoder.matches(request.password, timingDummyHash)
            false
        } else {
            passwordEncoder.matches(request.password, user.passwordHash)
        }
        if (user == null || !passwordMatches) {
            loginThrottle.recordFailure(email)
            securityEvents.emit("auth.login.failure", email = email, attrs = mapOf("reason" to "bad_credentials"))
            throw IllegalArgumentException("Invalid email or password")
        }
        if (user.disabledAt != null) {
            // Revealed only to someone who knows the password.
            securityEvents.emit("auth.suspended.blocked", userId = user.id)
            throw AccessDeniedException("This account is suspended")
        }
        securityEvents.emit("auth.login.success", userId = user.id)
        return createAuthResponse(user)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthResponse {
        // consume() deletes the presented token, so each refresh token works
        // exactly once — the response carries its replacement.
        val userId = refreshTokenService.consume(refreshToken) ?: run {
            securityEvents.emit("auth.refresh.failure")
            throw IllegalArgumentException("Invalid or expired refresh token")
        }

        val user = userRepository.findById(userId).orElse(null) ?: run {
            securityEvents.emit("auth.refresh.failure")
            throw IllegalArgumentException("Invalid or expired refresh token")
        }
        if (user.disabledAt != null) {
            securityEvents.emit("auth.suspended.blocked", userId = user.id)
            throw IllegalArgumentException("Invalid or expired refresh token")
        }

        return createAuthResponse(user)
    }

    /** Revokes the presented refresh session. Idempotent — unknown tokens are ignored. */
    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenService.revoke(refreshToken)
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        request.displayName?.let { user.displayName = it.trim().ifEmpty { null } }
        request.defaultCurrency?.let { user.defaultCurrency = it }
        request.measurementUnit?.let { user.measurementUnit = it }
        user.updatedAt = Instant.now()

        val saved = userRepository.save(user)
        return UserResponse(
            id = saved.id.toString(),
            email = saved.email,
            displayName = saved.displayName,
            defaultCurrency = saved.defaultCurrency,
            measurementUnit = saved.measurementUnit
        )
    }

    @Transactional
    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        PasswordRules.check(request.newPassword)
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw IllegalArgumentException("Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)!!
        user.updatedAt = Instant.now()
        userRepository.save(user)

        // A password change should end every existing session — if the user is
        // changing it because the old one leaked, stolen refresh tokens die here.
        refreshTokenService.revokeAllForUser(userId)
    }

    private fun createAuthResponse(user: User): AuthResponse {
        val userId = user.id.toString()
        return AuthResponse(
            accessToken = jwtTokenProvider.generateAccessToken(userId),
            refreshToken = refreshTokenService.issue(user.id!!),
            user = UserResponse(
                id = userId,
                email = user.email,
                displayName = user.displayName,
                defaultCurrency = user.defaultCurrency,
                measurementUnit = user.measurementUnit
            )
        )
    }
}

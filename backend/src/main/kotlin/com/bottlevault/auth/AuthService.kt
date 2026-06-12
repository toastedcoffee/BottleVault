package com.bottlevault.auth

import com.bottlevault.auth.dto.*
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.user.dto.ChangePasswordRequest
import com.bottlevault.user.dto.UpdateProfileRequest
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${app.registration.enabled}") private val registrationEnabled: Boolean
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (!registrationEnabled) {
            throw IllegalStateException("Registration is currently disabled")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw ResourceAlreadyExistsException("An account with this email already exists")
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName
        )
        val savedUser = userRepository.save(user)
        return createAuthResponse(savedUser)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return createAuthResponse(user)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthResponse {
        // consume() deletes the presented token, so each refresh token works
        // exactly once — the response carries its replacement.
        val userId = refreshTokenService.consume(refreshToken)
            ?: throw IllegalArgumentException("Invalid or expired refresh token")

        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Invalid or expired refresh token") }

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

        request.displayName?.let { user.displayName = it }
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
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw IllegalArgumentException("Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
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

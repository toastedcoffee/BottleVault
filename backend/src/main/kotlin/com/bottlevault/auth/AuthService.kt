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

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return createAuthResponse(user)
    }

    fun refresh(refreshToken: String): AuthResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid refresh token")
        }
        if (jwtTokenProvider.getTokenType(refreshToken) != "refresh") {
            throw IllegalArgumentException("Invalid token type")
        }

        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)
        val user = userRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow { IllegalArgumentException("User not found") }

        return createAuthResponse(user)
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
    }

    private fun createAuthResponse(user: User): AuthResponse {
        val userId = user.id.toString()
        return AuthResponse(
            accessToken = jwtTokenProvider.generateAccessToken(userId),
            refreshToken = jwtTokenProvider.generateRefreshToken(userId),
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

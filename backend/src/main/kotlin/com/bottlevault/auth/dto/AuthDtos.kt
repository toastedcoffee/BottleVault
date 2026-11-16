// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 254, message = "Email is too long")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    val password: String,

    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String? = null,

    @field:Size(max = 64)
    val inviteCode: String? = null,

    @field:Size(max = 2048)
    val turnstileToken: String? = null,
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Size(max = 254)
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(max = 1024)
    val password: String,

    @field:Size(max = 2048)
    val turnstileToken: String? = null,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String?,
    val defaultCurrency: String,
    val measurementUnit: String
)

data class RefreshRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

/** What the prebuilt frontend needs to know before anyone signs in. */
data class AuthConfigResponse(
    val registrationMode: String,
    val turnstileSiteKey: String?,
    val privacyUrl: String?,
)

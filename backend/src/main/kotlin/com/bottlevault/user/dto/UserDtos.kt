// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String? = null,

    @field:Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three-letter code")
    val defaultCurrency: String? = null,

    @field:Pattern(regexp = "^(ml|oz|cl)$", message = "Unit must be ml, oz or cl")
    val measurementUnit: String? = null,
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    @field:Size(max = 1024)
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, max = 72, message = "New password must be 8 to 72 characters")
    val newPassword: String,
)

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand.dto

import com.bottlevault.brand.Brand
import jakarta.validation.constraints.NotBlank

data class BrandResponse(
    val id: String,
    val displayName: String,
    val country: String?,
    val website: String?
) {
    companion object {
        fun from(brand: Brand) = BrandResponse(
            id = brand.id.toString(),
            displayName = brand.displayName,
            country = brand.country,
            website = brand.website
        )
    }
}

data class BrandCreateRequest(
    @field:NotBlank(message = "Brand name is required")
    val name: String,
    val country: String? = null,
    val website: String? = null
)

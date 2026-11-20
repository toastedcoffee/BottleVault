// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand.dto

import com.bottlevault.brand.Brand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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

/**
 * What a signed-in user may submit for a new brand. `website` is not accepted: the
 * app has never sent it, and the column stays for seeded rows only. An unknown JSON
 * field is ignored rather than refused, so an old client sending it still works.
 */
data class BrandCreateRequest(
    @field:NotBlank(message = "Brand name is required")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,

    @field:Size(max = 100, message = "must be at most 100 characters")
    val country: String? = null,
)

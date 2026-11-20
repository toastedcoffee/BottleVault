// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product.dto

import com.bottlevault.brand.dto.BrandResponse
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.product.Product
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class ProductResponse(
    val id: String,
    val displayName: String,
    val barcode: String?,
    val type: AlcoholType,
    val subtype: String?,
    val size: String?,
    val abv: BigDecimal?,
    val description: String?,
    val imageUrl: String?,
    val isUserCreated: Boolean,
    val brand: BrandResponse
) {
    companion object {
        fun from(product: Product) = ProductResponse(
            id = product.id.toString(),
            displayName = product.displayName,
            barcode = product.barcode,
            type = product.type,
            subtype = product.subtype,
            size = product.size,
            abv = product.abv,
            description = product.description,
            imageUrl = product.imageUrl,
            isUserCreated = product.isUserCreated,
            brand = BrandResponse.from(product.brand)
        )
    }
}

/**
 * What a signed-in user may submit for a new product. `description` and `imageUrl`
 * are not accepted: the app has never sent them, and the columns stay for seeded
 * rows only. An unknown JSON field is ignored rather than refused.
 */
data class ProductCreateRequest(
    @field:NotBlank(message = "Product name is required")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,

    @field:NotNull(message = "Brand ID is required")
    val brandId: String,

    @field:NotNull(message = "Alcohol type is required")
    val type: AlcoholType,

    /** Blank means "no barcode" and is stored as null; anything else is 8 to 14 digits. */
    @field:Pattern(regexp = "^\\s*$|^[0-9]{8,14}$", message = "must be 8 to 14 digits")
    val barcode: String? = null,

    @field:Size(max = 100, message = "must be at most 100 characters")
    val subtype: String? = null,

    @field:Size(max = 50, message = "must be at most 50 characters")
    val size: String? = null,

    @field:DecimalMin(value = "0.0", message = "must be between 0 and 100")
    @field:DecimalMax(value = "100.0", message = "must be between 0 and 100")
    val abv: BigDecimal? = null,
)

package com.bottlevault.brand.dto

import com.bottlevault.brand.Brand
import jakarta.validation.constraints.NotBlank

data class BrandResponse(
    val id: String,
    val name: String,
    val country: String?,
    val website: String?
) {
    companion object {
        fun from(brand: Brand) = BrandResponse(
            id = brand.id.toString(),
            name = brand.name,
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

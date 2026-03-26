package com.bottlevault.product.dto

import com.bottlevault.brand.dto.BrandResponse
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.product.Product
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class ProductResponse(
    val id: String,
    val name: String,
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
            name = product.name,
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

data class ProductCreateRequest(
    @field:NotBlank(message = "Product name is required")
    val name: String,

    @field:NotNull(message = "Brand ID is required")
    val brandId: String,

    @field:NotNull(message = "Alcohol type is required")
    val type: AlcoholType,

    val barcode: String? = null,
    val subtype: String? = null,
    val size: String? = null,
    val abv: BigDecimal? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

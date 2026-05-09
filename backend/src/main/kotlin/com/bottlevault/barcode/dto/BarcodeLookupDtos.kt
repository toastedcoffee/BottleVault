package com.bottlevault.barcode.dto

import com.bottlevault.product.dto.ProductResponse

data class BarcodeLookupResponse(
    val found: Boolean,
    val source: String?,
    val product: ProductResponse? = null,
    val externalProduct: ExternalProductData? = null
)

data class ExternalProductData(
    val name: String,
    val brandName: String?,
    val barcode: String,
    val size: String?,
    val abv: Double?,
    val imageUrl: String?,
    val categories: String?
)

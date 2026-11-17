// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle.dto

import com.bottlevault.bottle.Bottle
import com.bottlevault.common.model.BottleStatus
import com.bottlevault.product.dto.ProductResponse
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class BottleResponse(
    val id: String,
    val status: BottleStatus,
    val percentageLeft: Int,
    val purchaseDate: LocalDate?,
    val purchaseLocation: String?,
    val purchaseCost: BigDecimal?,
    val notes: String?,
    val rating: Int?,
    val storageLocation: String?,
    val imagePath: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val product: ProductResponse
) {
    companion object {
        fun from(bottle: Bottle) = BottleResponse(
            id = bottle.id.toString(),
            status = bottle.status,
            percentageLeft = bottle.percentageLeft,
            purchaseDate = bottle.purchaseDate,
            purchaseLocation = bottle.purchaseLocation,
            purchaseCost = bottle.purchaseCost,
            notes = bottle.notes,
            rating = bottle.rating,
            storageLocation = bottle.storageLocation,
            imagePath = bottle.imagePath,
            createdAt = bottle.createdAt,
            updatedAt = bottle.updatedAt,
            product = ProductResponse.from(bottle.product)
        )
    }
}

// Every free-text field is bounded so an oversize value is a 400 that names the
// field, never a database error, and money fits the DECIMAL(10,2) column exactly
// instead of being rounded by the database.
data class BottleCreateRequest(
    @field:NotNull(message = "Product ID is required")
    @field:Size(max = 36, message = "must be a product ID")
    val productId: String,

    val status: BottleStatus = BottleStatus.UNOPENED,

    @field:Min(0) @field:Max(100)
    val percentageLeft: Int = 100,

    val purchaseDate: LocalDate? = null,

    @field:Size(max = 255, message = "must be at most 255 characters")
    val purchaseLocation: String? = null,

    @field:DecimalMin(value = "0.00", message = "must not be negative")
    @field:Digits(integer = 8, fraction = 2, message = "must have at most 8 digits before the point and 2 after")
    val purchaseCost: BigDecimal? = null,

    @field:Size(max = 5000, message = "must be at most 5000 characters")
    val notes: String? = null,

    @field:Min(1) @field:Max(5)
    val rating: Int? = null,

    @field:Size(max = 255, message = "must be at most 255 characters")
    val storageLocation: String? = null
)

data class BottleUpdateRequest(
    val status: BottleStatus? = null,

    @field:Min(0) @field:Max(100)
    val percentageLeft: Int? = null,

    val purchaseDate: LocalDate? = null,

    @field:Size(max = 255, message = "must be at most 255 characters")
    val purchaseLocation: String? = null,

    @field:DecimalMin(value = "0.00", message = "must not be negative")
    @field:Digits(integer = 8, fraction = 2, message = "must have at most 8 digits before the point and 2 after")
    val purchaseCost: BigDecimal? = null,

    @field:Size(max = 5000, message = "must be at most 5000 characters")
    val notes: String? = null,

    @field:Min(1) @field:Max(5)
    val rating: Int? = null,

    @field:Size(max = 255, message = "must be at most 255 characters")
    val storageLocation: String? = null
)

data class StatusUpdateRequest(
    @field:NotNull(message = "Status is required")
    val status: BottleStatus
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

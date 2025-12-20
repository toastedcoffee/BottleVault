package com.toastedcoffee.bottlevault.data.model

import androidx.room.DatabaseView
import androidx.room.Embedded
import java.util.Date

@DatabaseView(
    "SELECT b.id, b.productId, b.userId, b.status, b.percentageLeft, " +
            "b.purchaseDate, b.purchaseLocation, b.purchaseCost, b.notes, b.rating, " +
            "b.storageLocation, b.imageLocalPath, b.hasCustomImage, " +
            "b.createdAt, b.lastModified, b.syncStatus, " +
            "p.name as productName, p.type, p.subtype, p.size, p.abv, " +
            "br.name as brandName, br.country " +
            "FROM bottles b " +
            "INNER JOIN products p ON b.productId = p.id " +
            "INNER JOIN brands br ON p.brandId = br.id"
)
data class BottleWithProduct(
    // Bottle fields
    val id: String,
    val productId: String,
    val userId: String,
    val status: BottleStatus,
    val percentageLeft: Int,
    val purchaseDate: Date?,
    val purchaseLocation: String?,
    val purchaseCost: Double?,
    val notes: String?,
    val rating: Int?,
    val storageLocation: String?,
    val imageLocalPath: String?,
    val hasCustomImage: Boolean,
    val createdAt: Date,
    val lastModified: Date,
    val syncStatus: SyncStatus,

    // Product fields (denormalized)
    val productName: String,
    val type: AlcoholType,
    val subtype: String?,
    val size: String?,
    val abv: Double?,
    val brandName: String,
    val country: String?
) {
    // Helper properties for backward compatibility
    val bottle: Bottle
        get() = Bottle(
            id = id,
            userId = userId,
            productId = productId,
            status = status,
            percentageLeft = percentageLeft,
            purchaseDate = purchaseDate,
            purchaseLocation = purchaseLocation,
            purchaseCost = purchaseCost,
            notes = notes,
            rating = rating,
            storageLocation = storageLocation,
            imageLocalPath = imageLocalPath,
            hasCustomImage = hasCustomImage,
            createdAt = createdAt,
            lastModified = lastModified,
            syncStatus = syncStatus
        )

    val product: ProductInfo
        get() = ProductInfo(
            name = productName,
            type = type,
            subtype = subtype,
            size = size,
            abv = abv,
            brandName = brandName,
            country = country
        )
}

// Helper data class for product info
data class ProductInfo(
    val name: String,
    val type: AlcoholType,
    val subtype: String?,
    val size: String?,
    val abv: Double?,
    val brandName: String,
    val country: String?
) {
    val brand: BrandInfo
        get() = BrandInfo(name = brandName, country = country)
}

data class BrandInfo(
    val name: String,
    val country: String?
)
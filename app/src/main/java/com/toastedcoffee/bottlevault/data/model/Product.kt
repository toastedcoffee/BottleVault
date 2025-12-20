package com.toastedcoffee.bottlevault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Brand::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["brandId"]),
        Index(value = ["barcode"], unique = true),
        Index(value = ["name"]),
        Index(value = ["type"])
    ]
)
data class Product(
    @PrimaryKey val id: String,
    val brandId: String, // FK to Brand
    val name: String, // "Jameson Irish Whiskey"
    val barcode: String? = null, // UPC/EAN
    val type: AlcoholType,
    val subtype: String? = null, // "Single Malt", "IPA", "Cabernet Sauvignon"
    val size: String? = null, // "750ml", "1L"
    val abv: Double? = null, // Alcohol by volume percentage
    val description: String? = null,
    val imageUrl: String? = null, // Official product image URL

    // Metadata
    val isUserCreated: Boolean = false, // true if user added, false if pre-seeded
    val createdAt: Date = Date(),

    // Sync metadata
    val serverId: String? = null,
    var syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModified: Date = Date()
)
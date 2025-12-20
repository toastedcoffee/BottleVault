package com.toastedcoffee.bottlevault.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "bottles",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["userId"]),
        Index(value = ["status"]),
        Index(value = ["syncStatus"]),
        Index(value = ["lastModified"]),
        Index(value = ["userId", "status"]) // Compound index for filtered queries
    ]
)
data class Bottle(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productId: String, // FK to Product
    val userId: String, // FK to User

    // Instance-specific data
    var status: BottleStatus = BottleStatus.UNOPENED,
    var percentageLeft: Int = 100, // 0-100

    // Purchase information
    var purchaseDate: Date? = null,
    var purchaseLocation: String? = null,
    var purchaseCost: Double? = null,

    // Personal data
    var notes: String? = null,
    var rating: Int? = null, // 1-5 stars
    var storageLocation: String? = null,

    // Images (local storage)
    var imageLocalPath: String? = null,
    var hasCustomImage: Boolean = false,

    // Future image sync support
    var imageServerId: String? = null,
    var imageServerUrl: String? = null,

    // Audit trail
    val createdAt: Date = Date(),
    var lastModified: Date = Date(),

    // Sync metadata
    val serverId: String? = null,
    var syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    var lastSyncAttempt: Date? = null,
    var serverVersion: Long = 0,
    var syncError: String? = null
)
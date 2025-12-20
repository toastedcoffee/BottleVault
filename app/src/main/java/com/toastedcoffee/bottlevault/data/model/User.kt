package com.toastedcoffee.bottlevault.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey val id: String, // Server-assigned user ID
    val email: String,
    var displayName: String? = null,
    var profileImageUrl: String? = null,

    // Account status
    val isEmailVerified: Boolean = false,
    val createdAt: Date = Date(),
    var lastActiveAt: Date = Date(),

    // Local app state
    val isCurrentUser: Boolean = false,
    var lastSyncTime: Date? = null,

    // Preferences
    var defaultCurrency: String = "USD",
    var measurementUnit: String = "ml" // ml, oz, cl
)
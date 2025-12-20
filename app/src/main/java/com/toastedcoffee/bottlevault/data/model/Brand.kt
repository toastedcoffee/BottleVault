package com.toastedcoffee.bottlevault.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "brands",
    indices = [Index(value = ["name"], unique = true)]
)
data class Brand(
    @PrimaryKey val id: String,
    val name: String,
    val country: String? = null,
    val website: String? = null,

    // Sync metadata
    val serverId: String? = null,
    var syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModified: Date = Date()
)
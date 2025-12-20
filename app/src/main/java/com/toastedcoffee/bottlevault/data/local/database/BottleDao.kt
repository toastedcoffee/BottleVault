package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import com.toastedcoffee.bottlevault.data.model.Bottle
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface BottleDao {
    @Query("SELECT * FROM bottles WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY lastModified DESC")
    fun getAllBottlesForUser(userId: String): Flow<List<Bottle>>

    @Query("SELECT * FROM bottles WHERE id = :bottleId LIMIT 1")
    suspend fun getBottleById(bottleId: String): Bottle?

    @Query("SELECT * FROM bottles WHERE userId = :userId AND status = :status AND syncStatus != 'PENDING_DELETE'")
    fun getBottlesByStatus(userId: String, status: BottleStatus): Flow<List<Bottle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottle(bottle: Bottle)

    @Update
    suspend fun updateBottle(bottle: Bottle)

    @Delete
    suspend fun deleteBottle(bottle: Bottle)

    @Query("UPDATE bottles SET syncStatus = 'PENDING_DELETE', lastModified = :deletedAt WHERE id = :bottleId")
    suspend fun markBottleForDeletion(bottleId: String, deletedAt: Date)

    @Query("SELECT * FROM bottles WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncBottles(): List<Bottle>

    @Query("UPDATE bottles SET syncStatus = :status, lastSyncAttempt = :timestamp, syncError = :error WHERE id = :bottleId")
    suspend fun updateSyncStatus(bottleId: String, status: SyncStatus, timestamp: Date, error: String?)
}
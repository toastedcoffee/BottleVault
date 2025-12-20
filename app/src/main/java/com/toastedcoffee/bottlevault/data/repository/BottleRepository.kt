package com.toastedcoffee.bottlevault.data.repository

import com.toastedcoffee.bottlevault.data.local.database.BottleDao
import com.toastedcoffee.bottlevault.data.local.database.BottleWithProductDao
import com.toastedcoffee.bottlevault.data.model.AlcoholType
import com.toastedcoffee.bottlevault.data.model.Bottle
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.BottleWithProduct
import com.toastedcoffee.bottlevault.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for bottle instance operations.
 *
 * RESPONSIBILITIES (ONLY):
 * - CRUD operations on user's bottle instances
 * - Filter/search bottles
 * - Update bottle status and consumption
 * - Handle soft-deletes (for sync)
 *
 * WHAT THIS REPOSITORY DOES NOT DO:
 * - Manage products/brands (use ProductRepository)
 * - Manage users (use UserRepository)
 * - Sync with backend (will be SyncManager - Month 4)
 *
 * WHY THE SPLIT:
 * Single Responsibility Principle.
 * Each repository has one clear purpose.
 */
@Singleton
class BottleRepository @Inject constructor(
    private val bottleDao: BottleDao,
    private val bottleWithProductDao: BottleWithProductDao,
    private val userRepository: UserRepository  // Inject other repository!
) {

    // ==========================================
    // READ OPERATIONS
    // ==========================================

    /**
     * Gets all bottles for current user with full product details.
     *
     * RETURN TYPE:
     * BottleWithProduct includes bottle + product + brand.
     * This is what the UI needs for displaying inventory.
     *
     * DATABASE VIEW:
     * Uses BottleWithProduct view (pre-joined in SQL).
     * Much faster than joining in code.
     *
     * FILTERING:
     * Automatically excludes bottles marked for deletion.
     * (syncStatus != PENDING_DELETE)
     *
     * @return Flow of all bottles (updates automatically)
     */
    fun getAllBottlesWithProducts(): Flow<List<BottleWithProduct>> = flow {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            // User logged in: show their bottles
            emit(bottleWithProductDao.getAllBottlesWithProductForUser(currentUser.id).first())
        } else {
            // Guest mode: empty list (or guest bottles if implemented)
            emit(emptyList())
        }
    }

    /**
     * Gets a single bottle by ID.
     *
     * USE CASE:
     * Editing a bottle - need current data to pre-fill form.
     *
     * @param bottleId The bottle's unique identifier
     * @return Bottle if found, null otherwise
     */
    suspend fun getBottleById(bottleId: String): Bottle? {
        return bottleDao.getBottleById(bottleId)
    }

    /**
     * Searches bottles by product or brand name.
     *
     * HOW IT WORKS:
     * SQL LIKE query on BottleWithProduct view.
     * Searches both product name and brand name.
     *
     * EXAMPLE:
     * Query "jack" finds:
     * - Bottles of "Jack Daniel's Old No. 7"
     * - Bottles of "Captain Jack Rum"
     *
     * @param query Search term (partial match OK)
     * @return Flow of matching bottles
     */
    fun searchBottles(query: String): Flow<List<BottleWithProduct>> = flow {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            emit(bottleWithProductDao.searchBottlesWithProduct(currentUser.id, query).first())
        } else {
            emit(emptyList())
        }
    }

    /**
     * Gets bottles filtered by alcohol type.
     *
     * USE CASE:
     * "Show me all my whiskies"
     *
     * @param type The alcohol type (WHISKEY, VODKA, etc.)
     * @return Flow of bottles of this type
     */
    fun getBottlesByType(type: AlcoholType): Flow<List<BottleWithProduct>> = flow {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            emit(bottleWithProductDao.getBottlesWithProductByType(currentUser.id, type).first())
        } else {
            emit(emptyList())
        }
    }

    /**
     * Gets bottles filtered by status.
     *
     * USE CASE:
     * - "Show only unopened bottles"
     * - "What have I already opened?"
     * - "What's empty and can be removed?"
     *
     * @param status The bottle status (UNOPENED, OPENED, EMPTY)
     * @return Flow of bottles with this status
     */
    fun getBottlesByStatus(status: BottleStatus): Flow<List<Bottle>> = flow {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            emit(bottleDao.getBottlesByStatus(currentUser.id, status).first())
        } else {
            emit(emptyList())
        }
    }

    // ==========================================
    // CREATE / UPDATE / DELETE OPERATIONS
    // ==========================================

    /**
     * Adds a new bottle to user's inventory.
     *
     * AUTOMATIC HANDLING:
     * - Sets userId to current user
     * - Sets creation timestamp
     * - Marks as PENDING_CREATE for sync
     * - Initializes lastModified
     *
     * VALIDATION:
     * Should be done by ViewModel before calling this.
     * Repository assumes data is valid.
     *
     * @param bottle The bottle to add
     */
    suspend fun insertBottle(bottle: Bottle) {
        val currentUserId = userRepository.getCurrentUserId()

        val bottleWithMetadata = bottle.copy(
            userId = currentUserId,
            createdAt = Date(),
            lastModified = Date(),
            syncStatus = SyncStatus.PENDING_CREATE
        )

        bottleDao.insertBottle(bottleWithMetadata)
    }

    /**
     * Updates an existing bottle.
     *
     * SYNC HANDLING:
     * If bottle was synced (syncStatus = SYNCED),
     * marks it PENDING_UPDATE so backend knows to sync.
     *
     * TIMESTAMP:
     * Always updates lastModified.
     *
     * @param bottle The bottle with updated data
     */
    suspend fun updateBottle(bottle: Bottle) {
        val updatedBottle = bottle.copy(
            lastModified = Date(),
            // Only change syncStatus if it was previously SYNCED
            syncStatus = if (bottle.syncStatus == SyncStatus.SYNCED) {
                SyncStatus.PENDING_UPDATE
            } else {
                bottle.syncStatus  // Keep existing status
            }
        )

        bottleDao.updateBottle(updatedBottle)
    }

    /**
     * Updates bottle status quickly (common operation).
     *
     * OPTIMIZATION:
     * Dedicated method for status changes.
     * Faster than loading full bottle, changing status, saving.
     *
     * USE CASE:
     * Quick status toggle: "Mark as opened", "Mark as empty"
     *
     * @param bottleId The bottle to update
     * @param newStatus The new status
     */
    suspend fun updateBottleStatus(bottleId: String, newStatus: BottleStatus) {
        val bottle = getBottleById(bottleId) ?: return
        updateBottle(bottle.copy(status = newStatus))
    }

    /**
     * Soft-deletes a bottle (marks for deletion, doesn't remove).
     *
     * WHY SOFT DELETE:
     * 1. Allows sync with backend (server needs to know what was deleted)
     * 2. Potential "undo" feature (future)
     * 3. Data recovery if user changes mind
     *
     * HARD DELETE:
     * Will happen during sync or manual cleanup.
     * For now, just mark as PENDING_DELETE.
     *
     * UI IMPACT:
     * getAllBottlesWithProducts() filters out PENDING_DELETE.
     * So bottle disappears from UI immediately.
     *
     * @param bottleId The bottle to delete
     */
    suspend fun deleteBottle(bottleId: String) {
        bottleDao.markBottleForDeletion(bottleId, Date())
    }

    /**
     * Hard-deletes a bottle (permanently removes from database).
     *
     * DANGER:
     * This is irreversible. Use carefully.
     *
     * WHEN TO USE:
     * - After successful sync to backend
     * - Manual cleanup of old soft-deleted bottles
     * - User explicitly confirms permanent deletion
     *
     * NORMAL USAGE:
     * Most code should use deleteBottle() (soft delete).
     *
     * @param bottle The bottle to permanently remove
     */
    suspend fun hardDeleteBottle(bottle: Bottle) {
        bottleDao.deleteBottle(bottle)
    }

    // ==========================================
    // SYNC-RELATED OPERATIONS (Future - Month 4)
    // ==========================================

    /**
     * Gets all bottles that need to sync to backend.
     *
     * SYNC STATUS INCLUDES:
     * - PENDING_CREATE (new bottles)
     * - PENDING_UPDATE (modified bottles)
     * - PENDING_DELETE (soft-deleted bottles)
     *
     * FUTURE USE (Month 4):
     * SyncManager will call this to get work queue.
     *
     * @return List of bottles needing sync
     */
    suspend fun getPendingSyncBottles(): List<Bottle> {
        return bottleDao.getPendingSyncBottles()
    }

    /**
     * Updates sync status after backend operation.
     *
     * WHEN TO CALL:
     * - After successful sync: status = SYNCED
     * - After sync failure: status = SYNC_FAILED
     * - After conflict: status = CONFLICT
     *
     * ERROR TRACKING:
     * Optional error message saved for debugging.
     *
     * @param bottleId The bottle that was synced
     * @param status New sync status
     * @param error Optional error message (if sync failed)
     */
    suspend fun updateSyncStatus(
        bottleId: String,
        status: SyncStatus,
        error: String? = null
    ) {
        bottleDao.updateSyncStatus(bottleId, status, Date(), error)
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Gets bottle with full product/brand details.
     *
     * CONVENIENCE:
     * Often need bottle + product + brand together.
     * This gets all three in one efficient call.
     *
     * @param bottleId The bottle's ID
     * @return BottleWithProduct or null if not found
     */
    suspend fun getBottleWithProduct(bottleId: String): BottleWithProduct? {
        val currentUser = userRepository.getCurrentUser() ?: return null

        val allBottles = bottleWithProductDao
            .getAllBottlesWithProductForUser(currentUser.id)
            .first()

        return allBottles.find { it.id == bottleId }
    }
}
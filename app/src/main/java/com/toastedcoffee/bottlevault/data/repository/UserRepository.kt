package com.toastedcoffee.bottlevault.data.repository

import com.toastedcoffee.bottlevault.data.local.database.UserDao
import com.toastedcoffee.bottlevault.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user-related operations.
 *
 * RESPONSIBILITIES:
 * - Manage current user session
 * - Handle user authentication state (future: login/logout)
 * - Provide user information to UI layer
 *
 * WHY SINGLETON:
 * We only want one instance managing user state across the app.
 * Hilt's @Singleton ensures this.
 *
 * WHY @Inject:
 * Hilt will automatically provide the UserDao when creating this repository.
 * You don't need to manually construct it.
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {

    // ==========================================
    // USER SESSION MANAGEMENT
    // ==========================================

    /**
     * Gets the currently logged-in user.
     *
     * HOW IT WORKS:
     * - Queries database for user marked as "current user"
     * - Returns null if no user is logged in (guest mode)
     *
     * WHEN TO USE:
     * - Before any bottle operations (need userId)
     * - Checking if user is logged in
     * - Displaying user info in UI
     *
     * @return Current user or null if guest mode
     */
    suspend fun getCurrentUser(): User? {
        return userDao.getCurrentUser()
    }

    /**
     * Gets current user as a Flow for reactive UI updates.
     *
     * FLOW vs SUSPEND:
     * - suspend: Gets data once (snapshot)
     * - Flow: Emits updates whenever data changes
     *
     * USE CASE:
     * If user info can change while app is running, use this.
     * Otherwise, use getCurrentUser() for simplicity.
     */
    fun getCurrentUserFlow(): Flow<User?> = flow {
        emit(userDao.getCurrentUser())
    }

    /**
     * Sets the active user session.
     *
     * IMPORTANT:
     * This clears any previous user session first.
     * Only one user can be "current" at a time.
     *
     * WHEN TO CALL:
     * - After user logs in (future: with backend)
     * - When creating guest user
     * - When switching users (future: multi-account support)
     *
     * @param user The user to set as current
     */
    suspend fun setCurrentUser(user: User) {
        // Step 1: Clear any existing current user
        userDao.clearCurrentUser()

        // Step 2: Insert this user with isCurrentUser = true
        userDao.insertUser(user.copy(isCurrentUser = true))

        // Step 3: Explicitly mark as current (redundant but safe)
        userDao.setCurrentUser(user.id)
    }

    /**
     * Updates the last sync time for current user.
     *
     * WHEN TO CALL:
     * After successful sync with backend.
     *
     * WHY TRACK THIS:
     * - Know when data was last updated
     * - Decide if sync is needed (e.g., older than 1 hour)
     * - Display "Last synced: 5 minutes ago" in UI
     */
    suspend fun updateLastSyncTime(userId: String, timestamp: Date) {
        userDao.updateLastSyncTime(userId, timestamp)
    }

    // ==========================================
    // HELPER FUNCTIONS
    // ==========================================

    /**
     * Quick check if user is logged in.
     *
     * USAGE:
     * if (userRepository.isLoggedIn()) {
     *     // Show sync button
     * } else {
     *     // Show "Sign up for sync" button
     * }
     */
    suspend fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * Gets current user ID or returns "guest" if none.
     *
     * SAFETY:
     * Prevents null pointer exceptions when userId is required.
     * Guest bottles are tied to "guest" userId.
     *
     * MIGRATION NOTE:
     * When user signs up, we'll need to migrate "guest" bottles
     * to their real userId. (Future task - Month 3)
     */
    suspend fun getCurrentUserId(): String {
        return getCurrentUser()?.id ?: "guest"
    }
}
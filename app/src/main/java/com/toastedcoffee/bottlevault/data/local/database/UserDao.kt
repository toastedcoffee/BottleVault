package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import com.toastedcoffee.bottlevault.data.model.User
import java.util.Date

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("UPDATE users SET isCurrentUser = 0")
    suspend fun clearCurrentUser()

    @Query("UPDATE users SET isCurrentUser = 1 WHERE id = :userId")
    suspend fun setCurrentUser(userId: String)

    @Query("UPDATE users SET lastSyncTime = :timestamp WHERE id = :userId")
    suspend fun updateLastSyncTime(userId: String, timestamp: Date)
}
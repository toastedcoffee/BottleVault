package com.toastedcoffee.bottlevault.ui.user

import com.toastedcoffee.bottlevault.data.model.User
import com.toastedcoffee.bottlevault.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val userRepository: UserRepository  // CHANGED: was BottleRepository
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    suspend fun initializeUser() {
        val existingUser = userRepository.getCurrentUser()  // FIXED: now uses UserRepository
        if (existingUser != null) {
            _currentUser.value = existingUser
        } else {
            createGuestUser()
        }
    }

    private suspend fun createGuestUser() {
        val guestUser = User(
            id = "guest_${UUID.randomUUID()}",
            email = "guest@local",
            displayName = "Guest User",
            isEmailVerified = false,
            createdAt = Date(),
            lastActiveAt = Date(),
            isCurrentUser = true
        )

        userRepository.setCurrentUser(guestUser)  // FIXED: now uses UserRepository
        _currentUser.value = guestUser
    }

    suspend fun signUpUser(email: String, displayName: String): Result<User> {
        return try {
            val newUser = User(
                id = "user_${UUID.randomUUID()}",
                email = email,
                displayName = displayName,
                isEmailVerified = false,
                createdAt = Date(),
                lastActiveAt = Date(),
                isCurrentUser = true
            )

            userRepository.setCurrentUser(newUser)  // FIXED: now uses UserRepository
            _currentUser.value = newUser
            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        _currentUser.value = null
        createGuestUser()
    }

    fun getCurrentUserId(): String {
        return _currentUser.value?.id ?: "guest"
    }
}
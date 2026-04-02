package com.bottlevault.user

import com.bottlevault.auth.AuthService
import com.bottlevault.auth.dto.UserResponse
import com.bottlevault.user.dto.ChangePasswordRequest
import com.bottlevault.user.dto.UpdateProfileRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/user")
class UserController(private val authService: AuthService) {

    @PutMapping("/profile")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        authentication: Authentication
    ): UserResponse = authService.updateProfile(userId(authentication), request)

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        authentication: Authentication
    ) = authService.changePassword(userId(authentication), request)

    private fun userId(authentication: Authentication): UUID =
        UUID.fromString(authentication.principal as String)
}

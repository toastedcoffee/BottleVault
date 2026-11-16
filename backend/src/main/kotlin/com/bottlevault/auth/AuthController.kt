// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.auth.dto.*
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    /** Public: what the prebuilt frontend needs before anyone signs in. */
    @GetMapping("/config")
    fun config(): ResponseEntity<AuthConfigResponse> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
            .body(authService.publicConfig())

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthResponse =
        authService.refresh(request.refreshToken)

    // No auth required: the refresh token in the body is the credential being
    // revoked, and revoking an unknown token is harmless (idempotent 204).
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshRequest) =
        authService.logout(request.refreshToken)
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.auth.dto.*
import com.bottlevault.common.security.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val turnstile: TurnstileVerifier,
    private val clientIpResolver: ClientIpResolver,
) {

    /** Public: what the prebuilt frontend needs before anyone signs in. */
    @GetMapping("/config")
    fun config(): ResponseEntity<AuthConfigResponse> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
            .body(authService.publicConfig())

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest, http: HttpServletRequest): AuthResponse {
        // Before the service: no invite is consumed and no account is looked up for
        // a request that has not passed the human check. The per-client limits have
        // already run in RateLimitFilter.
        turnstile.requireHuman(request.turnstileToken, "register", clientIpResolver.resolve(http))
        return authService.register(request)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): AuthResponse {
        turnstile.requireHuman(request.turnstileToken, "login", clientIpResolver.resolve(http))
        return authService.login(request)
    }

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

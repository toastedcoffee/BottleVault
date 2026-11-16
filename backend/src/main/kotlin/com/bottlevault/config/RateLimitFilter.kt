// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.common.security.ClientIpResolver
import com.bottlevault.common.security.RateLimiter
import com.bottlevault.common.security.SecurityEvents
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Per-client limits on everything under /api/auth/: a general per-path budget, plus a stricter
 * one for account creation. The client is who ClientIpResolver says it is —
 * never whatever a request's own headers claim.
 */
@Component
class RateLimitFilter(
    private val clientIpResolver: ClientIpResolver,
    private val rateLimiter: RateLimiter,
    private val securityEvents: SecurityEvents,
    @Value("\${app.rate-limit.max-attempts:10}") private val maxAttempts: Int,
    @Value("\${app.rate-limit.window-seconds:60}") private val windowSeconds: Long,
    @Value("\${app.rate-limit.auth.register-limit:5}") private val registerLimit: Int,
    @Value("\${app.rate-limit.auth.register-window:1h}") private val registerWindow: Duration,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !routedPath(request).startsWith("/api/auth/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val client = clientIpResolver.resolve(request)
        val path = routedPath(request)
        // Each real endpoint has its own budget. Any other /api/auth/ path (all 404s)
        // shares one, so a client cannot mint a fresh bucket per made-up path.
        val bucket = if (path in AUTH_ENDPOINTS) path else "/api/auth/*"
        var decision = rateLimiter.tryAcquire("auth:$bucket", client.rateLimitKey, maxAttempts, Duration.ofSeconds(windowSeconds))
        if (decision.allowed && path == "/api/auth/register" && request.method == "POST") {
            decision = rateLimiter.tryAcquire("register", client.rateLimitKey, registerLimit, registerWindow)
        }
        if (!decision.allowed) {
            // One event per client and bucket per window, not one per refused request:
            // a client hammering a closed bucket must not flood the security log and
            // push older evidence out of its size cap.
            if (rateLimiter.tryAcquire("trip-logged:$bucket", client.rateLimitKey, 1, Duration.ofSeconds(windowSeconds)).allowed) {
                securityEvents.emit("ratelimit.tripped", attrs = mapOf("bucket" to bucket), clientIp = client)
            }
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.setHeader(HttpHeaders.RETRY_AFTER, decision.retryAfterSeconds.toString())
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"status":429,"error":"Too Many Requests","message":"Too many attempts. Try again later."}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    // The decoded, normalized path the servlet container routes on — not the raw
    // requestURI. Tomcat routes /api/%61uth/login to the login handler, so keying on
    // requestURI would let it skip this filter (prefix check) or get its own bucket.
    // (nginx happens to normalize it first today; this filter must not depend on that.)
    private fun routedPath(request: HttpServletRequest): String =
        request.servletPath + (request.pathInfo ?: "")

    private companion object {
        // AuthController's endpoints. /config is listed ahead of the endpoint itself.
        val AUTH_ENDPOINTS = setOf(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/config"
        )
    }
}

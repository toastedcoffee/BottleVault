// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.auth.JwtAuthenticationFilter
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.http.HttpStatus
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter,
    @Value("\${springdoc.swagger-ui.enabled:false}") private val swaggerEnabled: Boolean,
    @Value("\${app.cors.allowed-origins:}") private val allowedOriginsCsv: String
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Unauthenticated requests must yield 401, not the default 403 —
            // the frontend's axios interceptor refreshes the access token only
            // on 401, so a 403 here strands users when the 15-minute access
            // token expires even though their refresh token is still valid.
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests { auth ->
                auth
                    // Framework-generated errors (unknown route, unreadable body, an
                    // unhandled exception) are rendered by the servlet container's
                    // ERROR dispatch to /error. The JWT filter doesn't run on that
                    // dispatch, so denyAll below answered every one of them with 401
                    // and the client then spent a refresh-token rotation retrying
                    // what was really a 404/400/500. Only that internal dispatch is
                    // permitted: a client requesting /error directly is an ordinary
                    // REQUEST dispatch and still hits denyAll.
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/brands/**", "/api/products/**").permitAll()

                // Only allow Swagger access when explicitly enabled
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                } else {
                    auth.requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").denyAll()
                }

                auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    // Spring Boot's UserDetailsServiceAutoConfiguration backs off once a
    // UserDetailsService bean exists. Without this it creates an in-memory
    // "user" and logs a generated password into the startup logs on every
    // boot. That account was already unreachable — this chain configures
    // neither httpBasic nor formLogin, and authentication happens purely
    // through the JWT filter above — so the credential was noise in logs that
    // are meant to be reviewed. Resolving nothing keeps it that way: any
    // future form/basic entry point fails closed rather than silently
    // accepting a generated login.
    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { username -> throw UsernameNotFoundException(username) }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // Comma-separated list from app.cors.allowed-origins (env var ALLOWED_ORIGINS in prod,
        // or configured per-profile in application-<profile>.yml for dev/test).
        val origins = allowedOriginsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val config = CorsConfiguration()
        config.allowedOrigins = origins
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type")
        config.allowCredentials = true
        config.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        return source
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.health

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The container healthcheck in docker-compose.prod.yml probes
 * /actuator/health/readiness unauthenticated, and the tunnel's startup is gated
 * downstream of that healthcheck. If this endpoint ever 404s (probe groups
 * disabled) or 401s (SecurityConfig permits only the exact /actuator/health
 * path, behind an anyRequest().denyAll()), the backend never reports healthy
 * and the site never comes up. These tests fail the build instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReadinessProbeIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `readiness probe answers unauthenticated with UP`() {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `liveness probe answers unauthenticated with UP`() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `liveness deliberately excludes the database so a DB blip does not kill a healthy process`() {
        // Readiness includes db (see below); liveness must not. A liveness probe
        // that fails on a transient DB blip would make an orchestrator restart
        // an otherwise healthy process instead of just taking it out of rotation.
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(jsonPath("$.components.db").doesNotExist())
    }

    @Test
    fun `readiness includes the database so an instance with no DB is not ready`() {
        // show-details is `always` only in application-test.yml; prod keeps the
        // unauthenticated response to a bare status so it leaks nothing.
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(jsonPath("$.components.db").exists())
    }

    @Test
    fun `plain health endpoint still answers unauthenticated`() {
        // Regression guard: the existing prod healthcheck uses this path, and
        // DEPLOY.md documents it. Widening the matcher must not narrow this.
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }
}

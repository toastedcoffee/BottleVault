// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * The drink types added for the seeded catalog (WINE, CIDER, MEAD) travel the
 * whole stack: created through the API, read back, filtered on, put in a
 * collection, and counted by the statistics endpoint. `products.type` is a
 * plain VARCHAR with no check constraint, so no migration is involved; this
 * proves the enum, the JSON binding and the type filters all agree.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductTypesIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private var accessToken: String = ""

    @BeforeEach
    fun setup() {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        RegisterRequest(
                            email = "types-${System.nanoTime()}@example.com",
                            password = "password123",
                            displayName = "Test User"
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        accessToken = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
    }

    @ParameterizedTest
    @ValueSource(strings = ["WINE", "CIDER", "MEAD"])
    fun `a product of a new type is created, read back, filtered on, collected and counted`(type: String) {
        val unique = UUID.randomUUID().toString().replace("-", "").take(12)
        val brandId = postJson("/api/brands", """{"name":"Brand $unique"}""")
            .andExpect(status().isCreated)
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asString() }

        val productId = postJson("/api/products", """{"name":"Product $unique","brandId":"$brandId","type":"$type"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value(type))
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asString() }

        // Read back by id, and found by the type filter.
        authed(get("/api/products/$productId")).andExpect(jsonPath("$.type").value(type))
        authed(get("/api/products").param("type", type).param("brandId", brandId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(productId))

        // Into the collection, found by the bottle type filter, counted by statistics.
        postJson("/api/bottles", """{"productId":"$productId"}""").andExpect(status().isCreated)
        authed(get("/api/bottles").param("type", type))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.type").value(type))
        authed(get("/api/statistics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.typeDistribution.length()").value(1))
            .andExpect(jsonPath("$.typeDistribution[0].type").value(type))
            .andExpect(jsonPath("$.typeDistribution[0].count").value(1))
    }

    private fun postJson(path: String, body: String): ResultActions =
        authed(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun authed(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder): ResultActions =
        mockMvc.perform(builder.header("Authorization", "Bearer $accessToken"))
}

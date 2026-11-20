// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class CatalogAuthIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `the catalog is not public`() {
        mockMvc.perform(get("/api/brands")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/products").param("search", "Old No. 7")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the suggest endpoints sit under the same rule as the lists`() {
        val brandList = mockMvc.perform(get("/api/brands").param("search", "jameson")).andReturn().response.status
        val brandSuggest = mockMvc.perform(get("/api/brands/suggest").param("name", "jamesen")).andReturn().response.status
        val productList = mockMvc.perform(get("/api/products").param("search", "black label")).andReturn().response.status
        val productSuggest = mockMvc.perform(
            get("/api/products/suggest").param("brandId", UUID.randomUUID().toString()).param("name", "blak label")
        ).andReturn().response.status

        assertEquals(brandList, brandSuggest)
        assertEquals(productList, productSuggest)
        assertEquals(401, brandSuggest)
    }
}

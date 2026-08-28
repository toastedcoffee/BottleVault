// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductSearchGuardIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var productService: ProductService

    @Test
    fun `searching with a term that normalizes to empty returns no results rather than every product`() {
        val results = productService.getProducts(brandId = null, type = null, search = "&")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searching with only punctuation returns no results`() {
        val results = productService.getProducts(brandId = null, type = null, search = "...")
        assertTrue(results.isEmpty())
    }
}

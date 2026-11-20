// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductSearchGuardIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var productService: ProductService

    @Test
    fun `searching with a term that normalizes to empty is refused rather than returning every product`() {
        assertThrows(IllegalArgumentException::class.java) {
            productService.getProducts(brandId = null, type = null, search = "&")
        }
    }

    @Test
    fun `searching with only punctuation is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            productService.getProducts(brandId = null, type = null, search = "...")
        }
    }
}

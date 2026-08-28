// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * Covers the write-path guard on Product creation: a submission that
 * normalizes to "" must be rejected rather than permanently squatting on
 * normalizedName = "" for its brand. See BrandCreationIntegrationTest for the
 * equivalent Brand-side guard.
 */
@SpringBootTest
class ProductCreationGuardIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var brandRepository: BrandRepository

    private lateinit var brandId: UUID

    @BeforeEach
    fun setUp() {
        val brand = brandRepository.save(
            Brand(
                displayName = "Suntory-${UUID.randomUUID()}",
                normalizedName = "suntory-${UUID.randomUUID()}",
            )
        )
        brandId = brand.id!!
    }

    @Test
    fun `a punctuation-only product name is rejected rather than silently squatting on normalizedName empty`() {
        val before = productRepository.count()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            productService.createProduct(
                ProductCreateRequest(name = "???", brandId = brandId.toString(), type = AlcoholType.WHISKEY)
            )
        }

        assertTrue(ex.message!!.contains("letter or number"))
        assertEquals(before, productRepository.count(), "no row should have been created")
    }

    @Test
    fun `a product name that is a single non-breaking space is rejected`() {
        // U+00A0 as a Kotlin escape, not a literal character, so it survives editing.
        val before = productRepository.count()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            productService.createProduct(
                ProductCreateRequest(name = "\u00A0", brandId = brandId.toString(), type = AlcoholType.WHISKEY)
            )
        }

        assertTrue(ex.message!!.contains("letter or number"))
        assertEquals(before, productRepository.count(), "no row should have been created")
    }
}

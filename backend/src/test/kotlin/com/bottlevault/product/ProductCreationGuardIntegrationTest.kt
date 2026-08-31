// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `a barcode submitted on a collision is attached to the existing barcode-less product`() {
        val existing = productRepository.save(
            Product(
                brand = brandRepository.findById(brandId).get(),
                displayName = "Original",
                normalizedName = "original",
                barcode = null,
                type = AlcoholType.WHISKEY
            )
        )

        val response = productService.createProduct(
            ProductCreateRequest(
                name = "Original",
                brandId = brandId.toString(),
                type = AlcoholType.WHISKEY,
                barcode = "5011007003005"
            )
        )

        assertEquals(existing.id.toString(), response.id, "should return the existing product, not create a new one")
        assertEquals("5011007003005", response.barcode)

        val reloaded = productRepository.findById(existing.id!!).get()
        assertEquals(
            "5011007003005",
            reloaded.barcode,
            "the barcode must actually be persisted, not just reflected in the response"
        )
    }

    @Test
    fun `an existing barcode on a collision is left untouched`() {
        val existing = productRepository.save(
            Product(
                brand = brandRepository.findById(brandId).get(),
                displayName = "Original",
                normalizedName = "original",
                barcode = "1111111111111",
                type = AlcoholType.WHISKEY
            )
        )

        val response = productService.createProduct(
            ProductCreateRequest(
                name = "Original",
                brandId = brandId.toString(),
                type = AlcoholType.WHISKEY,
                barcode = "2222222222222"
            )
        )

        assertEquals(existing.id.toString(), response.id)
        assertEquals("1111111111111", response.barcode, "the pre-existing barcode must win, not the newly submitted one")

        val reloaded = productRepository.findById(existing.id!!).get()
        assertEquals("1111111111111", reloaded.barcode)
    }

    @Test
    fun `a barcode already registered to a different product is rejected with 409, not silently dropped or 500`() {
        val otherBrandName = "OtherBrand-${UUID.randomUUID()}"
        val otherBrand = brandRepository.save(
            Brand(
                // One UUID, normalized from the display name, so the fixture models a
                // row the application could actually produce. Two separate
                // randomUUID() calls would leave normalizedName unrelated to
                // displayName, which no code path can create.
                displayName = otherBrandName,
                normalizedName = NameNormalizer.normalize(otherBrandName),
            )
        )
        val elsewhere = productRepository.save(
            Product(
                brand = otherBrand,
                displayName = "Elsewhere",
                normalizedName = "elsewhere",
                barcode = "3333333333333",
                type = AlcoholType.WHISKEY
            )
        )
        val existing = productRepository.save(
            Product(
                brand = brandRepository.findById(brandId).get(),
                displayName = "Original",
                normalizedName = "original",
                barcode = null,
                type = AlcoholType.WHISKEY
            )
        )

        assertThrows(ResourceAlreadyExistsException::class.java) {
            productService.createProduct(
                ProductCreateRequest(
                    name = "Original",
                    brandId = brandId.toString(),
                    type = AlcoholType.WHISKEY,
                    barcode = "3333333333333"
                )
            )
        }

        val reloadedExisting = productRepository.findById(existing.id!!).get()
        assertNull(reloadedExisting.barcode, "the existing barcode-less product must not gain a barcode that belongs elsewhere")

        val reloadedElsewhere = productRepository.findById(elsewhere.id!!).get()
        assertEquals("3333333333333", reloadedElsewhere.barcode, "the other product's barcode must be unaffected")
        assertNotNull(reloadedElsewhere.barcode)
    }
}

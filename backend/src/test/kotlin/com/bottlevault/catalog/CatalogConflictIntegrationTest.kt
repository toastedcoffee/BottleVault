// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.brand.BrandService
import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.Product
import com.bottlevault.product.ProductRepository
import com.bottlevault.product.ProductService
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.UUID

/**
 * The catalog writes that used to escape as unmapped 500s: a barcode held by another
 * product on the new-product path, and a unique-constraint race on either insert.
 * Both are now 409s (ResourceAlreadyExistsException), with nothing written.
 */
@SpringBootTest
class CatalogConflictIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var productService: ProductService
    @Autowired lateinit var brandService: BrandService
    @MockitoSpyBean lateinit var productRepository: ProductRepository
    @MockitoSpyBean lateinit var brandRepository: BrandRepository

    private lateinit var brand: Brand

    @BeforeEach
    fun setUp() {
        val name = "Conflict-${UUID.randomUUID()}"
        brand = brandRepository.save(Brand(displayName = name, normalizedName = NameNormalizer.normalize(name)))
    }

    @AfterEach
    fun tearDown() {
        reset(productRepository, brandRepository)
    }

    @Test
    fun `a new product with a barcode already on another product is a 409 and is not created`() {
        val barcode = "77${(System.nanoTime() % 1_000_000_000_000L).toString().padStart(12, '0')}"
        productRepository.save(
            Product(brand = brand, displayName = "Holder", normalizedName = "holder", barcode = barcode, type = AlcoholType.GIN)
        )

        assertThrows(ResourceAlreadyExistsException::class.java) {
            productService.createProduct(
                ProductCreateRequest(name = "Newcomer", brandId = brand.id.toString(), type = AlcoholType.GIN, barcode = barcode)
            )
        }
        assertNull(productRepository.findByBrandIdAndNormalizedName(brand.id!!, "newcomer"))
    }

    @Test
    fun `losing a product insert race is a 409, not a 500`() {
        doThrow(DataIntegrityViolationException("uq_products_brand_normalized_name"))
            .`when`(productRepository).saveAndFlush(any(Product::class.java))

        assertThrows(ResourceAlreadyExistsException::class.java) {
            productService.createProduct(
                ProductCreateRequest(name = "Raced", brandId = brand.id.toString(), type = AlcoholType.GIN)
            )
        }
    }

    @Test
    fun `losing a brand insert race is a 409, not a 500`() {
        doThrow(DataIntegrityViolationException("uq_brands_normalized_name"))
            .`when`(brandRepository).saveAndFlush(any(Brand::class.java))
        val name = "Raced-${UUID.randomUUID()}"

        assertThrows(ResourceAlreadyExistsException::class.java) {
            brandService.createBrand(BrandCreateRequest(name = name))
        }
        reset(brandRepository)
        assertEquals(null, brandRepository.findByNormalizedName(NameNormalizer.normalize(name)))
    }
}

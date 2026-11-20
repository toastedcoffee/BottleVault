// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.brand.BrandService
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.Product
import com.bottlevault.product.ProductRepository
import com.bottlevault.product.ProductService
import com.bottlevault.support.AbstractPostgresIntegrationTest
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

/**
 * The catalog N+1: listing 20 products over 20 brands used to cost one query for
 * the products and one more per brand. Pinned with upper bounds, not exact counts,
 * so a harmless extra statement elsewhere does not make the test brittle.
 */
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
class CatalogQueryCountIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var productService: ProductService
    @Autowired lateinit var brandService: BrandService
    @Autowired lateinit var brandRepository: BrandRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var entityManagerFactory: EntityManagerFactory

    private lateinit var term: String
    private lateinit var brands: List<Brand>

    @BeforeEach
    fun setUp() {
        term = "qc" + UUID.randomUUID().toString().replace("-", "").take(10)
        brands = (1..20).map { i ->
            val name = "Count $i ${UUID.randomUUID()}"
            brandRepository.save(Brand(displayName = name, normalizedName = NameNormalizer.normalize(name)))
        }
        brands.forEachIndexed { i, b ->
            val name = "$term product $i"
            productRepository.save(
                Product(brand = b, displayName = name, normalizedName = NameNormalizer.normalize(name), type = AlcoholType.RUM)
            )
        }
    }

    private fun statementsFor(block: () -> Unit): Long {
        val stats = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        stats.clear()
        block()
        return stats.prepareStatementCount
    }

    @Test
    fun `a product search over 20 brands is not one query per brand`() {
        var size = 0
        val count = statementsFor { size = productService.getProducts(null, null, term).size }
        assertEquals(20, size)
        assertTrue(count <= 2, "expected at most 2 statements, saw $count")
    }

    @Test
    fun `one brand's product list is not one query per product`() {
        val count = statementsFor { productService.getProducts(brands[0].id, null, null) }
        assertTrue(count <= 2, "expected at most 2 statements, saw $count")
    }

    @Test
    fun `a brand search is a single bounded query`() {
        val count = statementsFor { brandService.searchBrands(term) }
        assertTrue(count <= 2, "expected at most 2 statements, saw $count")
    }

    @Test
    fun `product suggestions read candidates and rows without a query per row`() {
        val b = brands[0]
        (1..5).forEach { i ->
            val name = "Near Name $i"
            productRepository.save(
                Product(brand = b, displayName = name, normalizedName = NameNormalizer.normalize(name), type = AlcoholType.RUM)
            )
        }
        var size = 0
        val count = statementsFor { size = productService.suggestProducts(b.id!!, "Near Name 0").size }
        assertEquals(5, size)
        assertTrue(count <= 3, "expected at most 3 statements, saw $count")
    }
}

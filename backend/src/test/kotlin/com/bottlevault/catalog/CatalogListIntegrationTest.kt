// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.Product
import com.bottlevault.product.ProductRepository
import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.bottlevault.support.registerForToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Spec H5 on real Postgres: catalog lists need a term (or, for products, a brand),
 * are capped at 20 and are ranked exact, prefix, rest in SQL, so the cap never cuts
 * an exact match. The product filters combine instead of the first one winning.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogListIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var brandRepository: BrandRepository
    @Autowired lateinit var productRepository: ProductRepository

    private lateinit var token: String

    /** A term no other row in the shared test database contains: letters and digits only. */
    private lateinit var term: String

    @BeforeEach
    fun setUp() {
        token = mockMvc.registerForToken(objectMapper, "lists")
        term = "rk" + UUID.randomUUID().toString().replace("-", "").take(10)
    }

    @Test
    fun `brand and product lists without a term are refused`() {
        assertEquals(400, fetch("/api/brands").status)
        assertEquals(400, fetch("/api/products").status)
        assertEquals(400, fetch("/api/products", "type" to "GIN").status, "a type alone is not enough")
    }

    @Test
    fun `a one-character or punctuation-only term is refused`() {
        assertEquals(400, fetch("/api/brands", "search" to "a").status)
        assertEquals(400, fetch("/api/brands", "search" to "&").status)
        assertEquals(400, fetch("/api/products", "search" to "a").status)
        assertEquals(400, fetch("/api/products", "search" to "a!").status)
    }

    @Test
    fun `a two-character term is enough`() {
        assertEquals(200, fetch("/api/brands", "search" to term.take(2)).status)
        assertEquals(200, fetch("/api/products", "search" to term.take(2)).status)
    }

    @Test
    fun `the exact brand ranks first even when 25 other brands contain the term`() {
        // "Aa …" sorts ahead of the exact name alphabetically, so without the SQL
        // ranking the exact row would fall past the cap of 20.
        repeat(25) { i -> brand("Aa $term ${"%02d".format(i)}") }
        brand("$term Prefix")
        val exact = brand(term)

        val names = names(fetch("/api/brands", "search" to term.uppercase()))

        assertEquals(20, names.size)
        assertEquals(exact.displayName, names[0])
        assertEquals("$term Prefix", names[1], "prefix ranks above contains")
    }

    @Test
    fun `a brand with more than 20 products lists 20, and the 21st is reachable with a term`() {
        val big = brand("Big $term")
        repeat(25) { i -> product(big, "Item ${"%02d".format(i + 1)}") }

        val first = names(fetch("/api/products", "brandId" to big.id.toString()))
        assertEquals(20, first.size)
        assertEquals((1..20).map { "Item ${"%02d".format(it)}" }, first)

        val narrowed = names(fetch("/api/products", "brandId" to big.id.toString(), "search" to "item 21"))
        assertEquals(listOf("Item 21"), narrowed)
    }

    @Test
    fun `the exact product ranks first even when 25 other products contain the term`() {
        val b = brand("Rank ${UUID.randomUUID()}")
        repeat(25) { i -> product(b, "Aa $term ${"%02d".format(i)}") }
        product(b, term)

        val names = names(fetch("/api/products", "search" to term))

        assertEquals(20, names.size)
        assertEquals(term, names[0])
    }

    @Test
    fun `brandId, type and search combine instead of the first filter winning`() {
        val a = brand("Comb A ${UUID.randomUUID()}")
        val other = brand("Comb B ${UUID.randomUUID()}")
        product(a, "$term Gin", AlcoholType.GIN)       // the only row matching all three
        product(a, "$term Rum", AlcoholType.RUM)       // excluded by type
        product(a, "Plain Gin", AlcoholType.GIN)       // excluded by search
        product(other, "$term Gin", AlcoholType.GIN)   // excluded by brand

        val rows = json(fetch("/api/products", "brandId" to a.id.toString(), "type" to "GIN", "search" to term))

        assertEquals(1, rows.size())
        assertEquals("$term Gin", rows[0]["displayName"].asText())
        assertEquals(a.id.toString(), rows[0]["brand"]["id"].asText())
    }

    @Test
    fun `with a brandId a one-character term is accepted`() {
        val b = brand("Short ${UUID.randomUUID()}")
        product(b, "X Edition")
        product(b, "Other")
        val names = names(fetch("/api/products", "brandId" to b.id.toString(), "search" to "x"))
        assertTrue("X Edition" in names)
    }

    // --- helpers ---------------------------------------------------------

    private fun brand(name: String): Brand =
        brandRepository.save(Brand(displayName = name, normalizedName = NameNormalizer.normalize(name)))

    private fun product(brand: Brand, name: String, type: AlcoholType = AlcoholType.WHISKEY): Product =
        productRepository.save(
            Product(brand = brand, displayName = name, normalizedName = NameNormalizer.normalize(name), type = type)
        )

    private fun fetch(path: String, vararg params: Pair<String, String>): MockHttpServletResponse {
        val request = get(path).header("Authorization", "Bearer $token")
        params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn().response
    }

    private fun json(response: MockHttpServletResponse): JsonNode {
        assertEquals(200, response.status, response.contentAsString)
        return objectMapper.readTree(response.contentAsString)
    }

    private fun names(response: MockHttpServletResponse): List<String> =
        json(response).let { node -> (0 until node.size()).map { node[it]["displayName"].asText() } }
}

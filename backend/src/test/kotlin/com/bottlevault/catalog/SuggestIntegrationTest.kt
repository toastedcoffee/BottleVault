// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.brand.Brand
import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameMatcher
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.Product
import com.bottlevault.product.ProductRepository
import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.bottlevault.support.registerForToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * "Did you mean" (design §3.8) against the whole catalog. Each fixture name carries
 * a random word, so rows from other test classes in the shared database are never
 * near enough to interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuggestIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var brandRepository: BrandRepository
    @Autowired lateinit var productRepository: ProductRepository

    private lateinit var token: String

    /** A random letters-only word, so typos of it are near nothing else in the database. */
    private lateinit var word: String

    @BeforeEach
    fun setUp() {
        token = mockMvc.registerForToken(objectMapper, "suggest")
        // Ten random letters: two such words are almost never within three edits.
        word = (1..10).map { ('a'..'z').random() }.joinToString("")
    }

    @Test
    fun `a one-letter typo suggests the existing brand`() {
        val existing = brand("Wilderness $word Trail")
        assertEquals(listOf(existing.displayName), brandSuggestions("Wilderness $word tlTrail"))
    }

    @Test
    fun `a leading article suggests the existing brand, both ways round`() {
        val existing = brand("Mac $word")
        assertEquals(listOf(existing.displayName), brandSuggestions("The Mac $word"))
        val withArticle = brand("The Glen $word")
        assertEquals(listOf(withArticle.displayName), brandSuggestions("Glen $word"))
    }

    @ParameterizedTest(name = "suffix \"{0}\" suggests the bare name")
    @MethodSource("suffixes")
    fun `each producer suffix suggests the existing brand`(suffix: String) {
        val existing = brand("Sierra $word")
        assertEquals(listOf(existing.displayName), brandSuggestions("Sierra $word $suffix"))
    }

    @Test
    fun `an unrelated name suggests nothing`() {
        brand("Sierra $word")
        assertEquals(emptyList<String>(), brandSuggestions("Completely Different ${UUID.randomUUID()}"))
    }

    @Test
    fun `the exact name is never offered as a suggestion`() {
        brand("Exact $word")
        assertEquals(emptyList<String>(), brandSuggestions("EXACT $word!"))
    }

    @Test
    fun `a term shorter than three characters suggests nothing`() {
        assertEquals(emptyList<String>(), brandSuggestions("ab"))
        assertEquals(emptyList<String>(), brandSuggestions("a!b"))
        assertEquals(emptyList<String>(), brandSuggestions(null))
    }

    @Test
    fun `at most five suggestions, nearest first`() {
        val names = (0..6).map { brand("Many $word $it").displayName }
        val got = brandSuggestions("Many $word")
        assertEquals(5, got.size)
        assertTrue(names.containsAll(got))
    }

    @Test
    fun `product suggestions are scoped to the brand and follow the same rules`() {
        val b = brand("Holder $word")
        val other = brand("Elsewhere $word")
        val near = product(b, "Small Batch $word")
        product(other, "Small Batch $word")
        product(b, "Something Else Entirely")

        assertEquals(listOf(near.displayName), productSuggestions(b, "Smal Batch $word"))
        assertEquals(emptyList<String>(), productSuggestions(b, "Small Batch $word"), "exact is not offered")
        assertEquals(emptyList<String>(), productSuggestions(b, "sm"))
    }

    @Test
    fun `product suggestions need a brandId and an unknown brand suggests nothing`() {
        assertEquals(400, call("/api/products/suggest", "name" to "anything").status)
        assertEquals("[]", call("/api/products/suggest", "brandId" to UUID.randomUUID().toString(), "name" to "anything").contentAsString)
    }

    // --- helpers ---------------------------------------------------------

    private fun brand(name: String): Brand =
        brandRepository.save(Brand(displayName = name, normalizedName = NameNormalizer.normalize(name)))

    private fun product(brand: Brand, name: String): Product =
        productRepository.save(
            Product(brand = brand, displayName = name, normalizedName = NameNormalizer.normalize(name), type = AlcoholType.BEER)
        )

    private fun brandSuggestions(name: String?): List<String> =
        names(call("/api/brands/suggest", *listOfNotNull(name?.let { "name" to it }).toTypedArray()))

    private fun productSuggestions(brand: Brand, name: String): List<String> =
        names(call("/api/products/suggest", "brandId" to brand.id.toString(), "name" to name))

    private fun call(path: String, vararg params: Pair<String, String>): MockHttpServletResponse {
        val request = get(path).header("Authorization", "Bearer $token")
        params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn().response
    }

    private fun names(response: MockHttpServletResponse): List<String> {
        assertEquals(200, response.status, response.contentAsString)
        val node = objectMapper.readTree(response.contentAsString)
        return (0 until node.size()).map { node[it]["displayName"].asText() }
    }

    companion object {
        @JvmStatic
        fun suffixes() = NameMatcher.PRODUCER_SUFFIXES
    }
}

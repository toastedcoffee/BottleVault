// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.bottlevault.support.registerForToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.random.Random

/**
 * "Did you mean" reads every candidate name and scores it in memory, with no cache.
 * This measures both suggest endpoints, end to end through MockMvc, against a
 * catalog the size of the #12 seed: 2,638 brands and 8,635 products, the longest
 * names 42 and 96 characters, and one brand holding 600 products.
 *
 * The data is synthetic, generated here from a fixed seed, and rolled back with the
 * test transaction so no other class sees it. The bound is deliberately generous
 * (2 s) so a slow CI runner does not flake; the measured numbers are printed and
 * recorded in the PR.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuggestTimingIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `suggest stays fast at seed size`() {
        val random = Random(12)
        val brands = generateNames(random, BRANDS, maxWords = 4, maxLength = BRAND_MAX)
        val brandIds = brands.map { UUID.randomUUID() }
        jdbc.batchUpdate(
            "INSERT INTO brands (id, display_name, normalized_name) VALUES (?, ?, ?)",
            brands.mapIndexed { i, n -> arrayOf<Any>(brandIds[i], n, NameNormalizer.normalize(n)) }
        )

        // Brand 0 holds 600 products; the rest are dealt round-robin over the others.
        val productRows = mutableListOf<Array<Any>>()
        val bigBrandProducts = generateNames(random, BIG_BRAND_PRODUCTS, maxWords = 8, maxLength = PRODUCT_MAX)
        bigBrandProducts.forEach { productRows += productRow(brandIds[0], it) }
        val rest = generateNames(random, PRODUCTS - BIG_BRAND_PRODUCTS, maxWords = 8, maxLength = PRODUCT_MAX)
        rest.forEachIndexed { i, n -> productRows += productRow(brandIds[1 + i % (BRANDS - 1)], n) }
        jdbc.batchUpdate(
            "INSERT INTO products (id, brand_id, display_name, normalized_name, type) VALUES (?, ?, ?, ?, 'WHISKEY')",
            productRows
        )

        assertEquals(BRANDS, brands.size)
        assertEquals(PRODUCTS, productRows.size)
        assertEquals(BRAND_MAX, brands.maxOf { it.length })
        assertEquals(PRODUCT_MAX, (bigBrandProducts + rest).maxOf { it.length })

        val token = mockMvc.registerForToken(objectMapper, "timing")
        val brandTypo = typo(brands.drop(1).first { it.length >= 15 })
        val productTypo = typo(bigBrandProducts.drop(1).first { it.length >= 30 })

        val brandMs = measure { suggest(token, "/api/brands/suggest", "name" to brandTypo) }
        val productMs = measure {
            suggest(token, "/api/products/suggest", "brandId" to brandIds[0].toString(), "name" to productTypo)
        }

        println("SUGGEST-TIMING brands/suggest ms: cold=${brandMs.first()} warm=${brandMs.drop(1)}")
        println("SUGGEST-TIMING products/suggest ms: cold=${productMs.first()} warm=${productMs.drop(1)}")
        (brandMs + productMs).forEach { assertTrue(it < 2_000, "a suggest call took $it ms") }
    }

    /** One cold call, then five warm ones; each in milliseconds. */
    private fun measure(call: () -> Int): List<Long> = (0..5).map {
        val start = System.nanoTime()
        val found = call()
        assertTrue(found >= 1, "the typo should suggest the row it was made from")
        (System.nanoTime() - start) / 1_000_000
    }

    private fun suggest(token: String, path: String, vararg params: Pair<String, String>): Int {
        val request = get(path).header("Authorization", "Bearer $token")
        params.forEach { (k, v) -> request.param(k, v) }
        val response = mockMvc.perform(request).andReturn().response
        assertEquals(200, response.status, response.contentAsString)
        return objectMapper.readTree(response.contentAsString).size()
    }

    private fun productRow(brandId: UUID, name: String) =
        arrayOf<Any>(UUID.randomUUID(), brandId, name, NameNormalizer.normalize(name))

    /** Changes the last letter: a one-edit typo that leaves any leading article alone. */
    private fun typo(name: String): String =
        name.dropLast(1) + (if (name.last() == 'q') 'x' else 'q')

    /**
     * [count] distinct names (distinct once normalized) of one to [maxWords] invented
     * words, at most [maxLength] characters, and exactly one of them [maxLength] long.
     */
    private fun generateNames(random: Random, count: Int, maxWords: Int, maxLength: Int): List<String> {
        val seen = HashSet<String>()
        val names = ArrayList<String>(count)
        fun word(): String {
            val syllables = 1 + random.nextInt(3)
            val w = (1..syllables).joinToString("") {
                CONSONANTS[random.nextInt(CONSONANTS.length)].toString() + VOWELS[random.nextInt(VOWELS.length)]
            }
            return w.replaceFirstChar { it.uppercase() }
        }
        val longest = buildString {
            while (length < maxLength) append(if (isEmpty()) "" else " ").append(word())
        }.take(maxLength).trimEnd().padEnd(maxLength, 'x')
        seen += NameNormalizer.normalize(longest)
        names += longest
        while (names.size < count) {
            val name = (1..(1 + random.nextInt(maxWords))).joinToString(" ") { word() }.take(maxLength).trim()
            if (name.isNotEmpty() && seen.add(NameNormalizer.normalize(name))) names += name
        }
        return names
    }

    private companion object {
        const val BRANDS = 2_638
        const val PRODUCTS = 8_635
        const val BIG_BRAND_PRODUCTS = 600
        const val BRAND_MAX = 42
        const val PRODUCT_MAX = 96
        const val CONSONANTS = "bcdfghklmnprstvw"
        const val VOWELS = "aeiou"
    }
}

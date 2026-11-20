// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.catalog

import com.bottlevault.brand.BrandRepository
import com.bottlevault.product.ProductRepository
import com.bottlevault.support.AbstractPostgresIntegrationTest
import com.bottlevault.support.registerForToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.util.UUID

/**
 * Spec H3: every user-typed catalog field has a bound, checked at N (accepted) and
 * N+1 (400 naming the field). Fields the app never sent are no longer accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogBoundsIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var brandRepository: BrandRepository
    @Autowired lateinit var productRepository: ProductRepository

    private lateinit var token: String
    private lateinit var brandId: String

    @BeforeEach
    fun setUp() {
        token = mockMvc.registerForToken(objectMapper, "bounds")
        brandId = postBrand(brand(unique("Bounds"))).json()["id"].asText()
    }

    // --- brand -----------------------------------------------------------

    @Test
    fun `brand name of 255 characters is accepted and 256 is refused`() {
        assertEquals(201, postBrand(brand(unique("B").padEnd(255, 'x'))).status)
        assertBadRequest(postBrand(brand(unique("B").padEnd(256, 'x'))), "name")
    }

    @Test
    fun `brand country of 100 characters is accepted and 101 is refused`() {
        assertEquals(201, postBrand(brand(unique("C")).put("country", "c".repeat(100))).status)
        assertBadRequest(postBrand(brand(unique("C")).put("country", "c".repeat(101))), "country")
    }

    @Test
    fun `a brand website is ignored, not stored`() {
        val response = postBrand(brand(unique("W")).put("website", "javascript:alert(1)"))
        assertEquals(201, response.status)
        assertTrue(response.json().absentOrNull("website"))
        assertNull(brandRepository.findById(UUID.fromString(response.json()["id"].asText())).get().website)
    }

    // --- product ---------------------------------------------------------

    @ParameterizedTest(name = "{0} at {1} characters is accepted, at {1}+1 refused")
    @CsvSource("name, 255", "subtype, 100", "size, 50")
    fun `product text fields are bounded`(field: String, max: Int) {
        val ok = product(unique("P")).put(field, unique("v").padEnd(max, 'x'))
        assertEquals(201, postProduct(ok).status)
        val tooLong = product(unique("P")).put(field, unique("v").padEnd(max + 1, 'x'))
        assertBadRequest(postProduct(tooLong), field)
    }

    @ParameterizedTest(name = "abv {0} -> {1}")
    @CsvSource("-0.1, 400", "0, 201", "100, 201", "100.1, 400")
    fun `abv must be between 0 and 100`(abv: String, expected: Int) {
        val response = postProduct(product(unique("A")).put("abv", abv.toBigDecimal()))
        assertEquals(expected, response.status, response.contentAsString)
        if (expected == 400) assertBadRequest(response, "abv")
    }

    @ParameterizedTest(name = "barcode of {0} digits -> {1}")
    @CsvSource("7, 400", "8, 201", "14, 201", "15, 400")
    fun `barcode must be 8 to 14 digits`(digits: Int, expected: Int) {
        val response = postProduct(product(unique("D")).put("barcode", uniqueDigits(digits)))
        assertEquals(expected, response.status, response.contentAsString)
        if (expected == 400) assertBadRequest(response, "barcode")
    }

    @Test
    fun `a barcode with letters is refused`() {
        assertBadRequest(postProduct(product(unique("L")).put("barcode", "abcdefgh")), "barcode")
    }

    @Test
    fun `blank barcodes are stored as null, so two of them no longer collide`() {
        val first = postProduct(product(unique("E")).put("barcode", ""))
        val second = postProduct(product(unique("E")).put("barcode", "   "))
        assertEquals(201, first.status, first.contentAsString)
        assertEquals(201, second.status, second.contentAsString)
        for (r in listOf(first, second)) {
            assertTrue(r.json().absentOrNull("barcode"))
            assertNull(productRepository.findById(UUID.fromString(r.json()["id"].asText())).get().barcode)
        }
    }

    @Test
    fun `product description and imageUrl are ignored, not stored`() {
        val response = postProduct(
            product(unique("I")).put("description", "x".repeat(10_000)).put("imageUrl", "javascript:alert(1)")
        )
        assertEquals(201, response.status)
        val row = productRepository.findById(UUID.fromString(response.json()["id"].asText())).get()
        assertNull(row.description)
        assertNull(row.imageUrl)
    }

    @Test
    fun `a name inside the display cap whose normalized form is too long is a 400, not a 500`() {
        // Each Hangul syllable decomposes under NFD into jamo, which are letters and
        // survive normalization, so 200 syllables normalize to about 500 characters.
        val response = postProduct(product("한".repeat(200)))
        assertEquals(400, response.status, response.contentAsString)
        assertTrue(response.json()["message"].asText().contains("too long"))
    }

    // --- helpers ---------------------------------------------------------

    private fun unique(prefix: String) = "$prefix-${UUID.randomUUID()}"

    private fun uniqueDigits(n: Int): String =
        (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString().padStart(19, '1').take(n)

    private fun brand(name: String): ObjectNode = objectMapper.createObjectNode().put("name", name)

    private fun product(name: String): ObjectNode =
        objectMapper.createObjectNode().put("name", name).put("brandId", brandId).put("type", "WHISKEY")

    private fun postBrand(body: ObjectNode) = post("/api/brands", body)
    private fun postProduct(body: ObjectNode) = post("/api/products", body)

    private fun post(path: String, body: ObjectNode): MockHttpServletResponse =
        mockMvc.perform(
            post(path).header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())
        ).andReturn().response

    private fun MockHttpServletResponse.json() = objectMapper.readTree(contentAsString)

    /** Null fields are left out of responses, so "null" reads as absent or JSON null. */
    private fun tools.jackson.databind.JsonNode.absentOrNull(field: String) = get(field)?.isNull ?: true

    private fun assertBadRequest(response: MockHttpServletResponse, field: String) {
        assertEquals(400, response.status, response.contentAsString)
        assertTrue(
            response.json()["message"].asText().startsWith("$field:"),
            "the message names the field: ${response.contentAsString}"
        )
    }
}

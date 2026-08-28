// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandCreationIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var brandService: BrandService

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Test
    fun `submitting a casing variant returns the existing brand rather than throwing`() {
        val before = brandRepository.count()

        val response = brandService.createBrand(BrandCreateRequest(name = "JAMESON"))

        assertEquals("Jameson", response.displayName)
        assertEquals(before, brandRepository.count(), "no new row should have been created")
    }

    @Test
    fun `submitting a punctuation variant returns the existing brand`() {
        val response = brandService.createBrand(BrandCreateRequest(name = "jack daniels"))
        assertEquals("Jack Daniel's", response.displayName)
    }

    @Test
    fun `a genuinely new brand is created with its submitted casing preserved`() {
        val response = brandService.createBrand(BrandCreateRequest(name = "BenRiach"))
        assertEquals("BenRiach", response.displayName)

        val stored = brandRepository.findByNormalizedName("benriach")
        assertEquals("BenRiach", stored!!.displayName)
    }

    @Test
    fun `a new brand has surrounding whitespace stripped from its display name`() {
        val response = brandService.createBrand(BrandCreateRequest(name = "  Kirkland  Signature  "))
        assertEquals("Kirkland Signature", response.displayName)
    }

    @Test
    fun `searching with a term that normalizes to empty returns no results rather than every brand`() {
        val results = brandService.searchBrands("&")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searching with only punctuation returns no results`() {
        val results = brandService.searchBrands("...")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `a punctuation-only name is rejected rather than silently squatting on normalizedName empty`() {
        val before = brandRepository.count()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            brandService.createBrand(BrandCreateRequest(name = "???"))
        }

        assertTrue(ex.message!!.contains("letter or number"))
        assertEquals(before, brandRepository.count(), "no row should have been created")
    }

    @Test
    fun `a name that is a single non-breaking space is rejected`() {
        // U+00A0 as a Kotlin escape, not a literal character, so it survives editing.
        val before = brandRepository.count()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            brandService.createBrand(BrandCreateRequest(name = "\u00A0"))
        }

        assertTrue(ex.message!!.contains("letter or number"))
        assertEquals(before, brandRepository.count(), "no row should have been created")
    }
}

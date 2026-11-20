// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

@SpringBootTest
class BrandSearchIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Test
    fun `findByNormalizedName locates a seeded brand regardless of submitted casing`() {
        val found = brandRepository.findByNormalizedName(NameNormalizer.normalize("JAMESON"))
        assertNotNull(found)
        assertEquals("Jameson", found!!.displayName)
    }

    @Test
    fun `findByNormalizedName ignores punctuation differences`() {
        val found = brandRepository.findByNormalizedName(NameNormalizer.normalize("jack daniels"))
        assertNotNull(found)
        assertEquals("Jack Daniel's", found!!.displayName)
    }

    @Test
    fun `searchRanked matches an alias that appears in no name field`() {
        val hibiki = Brand(
            displayName = "Hibiki",
            normalizedName = NameNormalizer.normalize("Hibiki"),
            aliases = "Suntory",
            normalizedAliases = NameNormalizer.normalize("Suntory"),
        )
        brandRepository.save(hibiki)

        val results = brandRepository.searchRanked(NameNormalizer.normalize("suntory"), PageRequest.of(0, 20))

        assertTrue(results.any { it.displayName == "Hibiki" })
    }

    @Test
    fun `searchRanked still matches on the brand name itself`() {
        val results = brandRepository.searchRanked(NameNormalizer.normalize("glen"), PageRequest.of(0, 20))
        assertTrue(results.any { it.displayName == "Glenfiddich" })
    }
}

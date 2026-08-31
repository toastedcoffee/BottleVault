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
    fun `searchByNormalized matches an alias that appears in no name field`() {
        val hibiki = Brand(
            displayName = "Hibiki",
            normalizedName = NameNormalizer.normalize("Hibiki"),
            aliases = "Suntory",
            normalizedAliases = NameNormalizer.normalize("Suntory"),
        )
        brandRepository.save(hibiki)

        val results = brandRepository.searchByNormalized(NameNormalizer.normalize("suntory"))

        assertTrue(results.any { it.displayName == "Hibiki" })
    }

    @Test
    fun `searchByNormalized still matches on the brand name itself`() {
        val results = brandRepository.searchByNormalized(NameNormalizer.normalize("glen"))
        assertTrue(results.any { it.displayName == "Glenfiddich" })
    }
}

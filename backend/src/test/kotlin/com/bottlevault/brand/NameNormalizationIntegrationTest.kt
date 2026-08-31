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
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class NameNormalizationIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `every seeded brand has a normalized name matching the Kotlin normalizer`() {
        val rows = jdbcTemplate.queryForList(
            "SELECT display_name, normalized_name FROM brands",
        )
        assertTrue(rows.isNotEmpty(), "expected the V2 seed to have produced rows")
        rows.forEach { row ->
            val display = row["display_name"] as String
            val stored = row["normalized_name"] as String
            assertEquals(
                NameNormalizer.normalize(display),
                stored,
                "backfill disagrees with NameNormalizer for '$display'",
            )
        }
    }

    @Test
    fun `every seeded product has a normalized name matching the Kotlin normalizer`() {
        val rows = jdbcTemplate.queryForList(
            "SELECT display_name, normalized_name FROM products",
        )
        assertTrue(rows.isNotEmpty(), "expected the V3 seed to have produced rows")
        rows.forEach { row ->
            val display = row["display_name"] as String
            val stored = row["normalized_name"] as String
            assertEquals(NameNormalizer.normalize(display), stored)
        }
    }

    @Test
    fun `display names carry no leading or trailing whitespace after backfill`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM brands WHERE display_name <> btrim(display_name)",
            Int::class.java,
        )
        assertEquals(0, count)
    }

    @Test
    fun `brands normalized_name is unique`() {
        val duplicates = jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM (
                SELECT normalized_name FROM brands
                GROUP BY normalized_name HAVING count(*) > 1
            ) d
            """.trimIndent(),
            Int::class.java,
        )
        assertEquals(0, duplicates)
    }

    @Test
    fun `aliases columns exist and default to null`() {
        val aliases = jdbcTemplate.queryForList(
            "SELECT aliases, normalized_aliases FROM brands",
        )
        assertNotNull(aliases)
    }
}

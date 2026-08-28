// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BrandRepository : JpaRepository<Brand, UUID> {

    /**
     * The duplicate guard and the lookup path. Callers must pass a value already
     * run through NameNormalizer.normalize, so this stays an exact match against
     * the unique column.
     */
    fun findByNormalizedName(normalizedName: String): Brand?

    /**
     * Search spans the brand's own normalized name, its normalized aliases, and
     * the normalized names of its products. Aliases are what let "suntory" find
     * Hibiki, where the term appears in no name field at all.
     */
    @Query(
        """
        SELECT DISTINCT b FROM Brand b
        LEFT JOIN Product p ON p.brand = b
        WHERE b.normalizedName LIKE CONCAT('%', :search, '%')
           OR b.normalizedAliases LIKE CONCAT('%', :search, '%')
           OR p.normalizedName LIKE CONCAT('%', :search, '%')
        ORDER BY b.displayName
        """
    )
    fun searchByNormalized(search: String): List<Brand>
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import org.springframework.data.domain.Pageable
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
     *
     * Ranked in SQL (exact name, then prefix, then the rest) so that the page cap
     * can never cut an exact match off the end. The product match is an EXISTS
     * rather than a join: a join needs DISTINCT, and Postgres refuses DISTINCT with
     * an ORDER BY expression that is not in the select list.
     *
     * [search] must already be normalized: it then holds only letters, digits and
     * single spaces, so it carries no LIKE wildcard.
     */
    @Query(
        """
        SELECT b FROM Brand b
        WHERE b.normalizedName LIKE CONCAT('%', :search, '%')
           OR b.normalizedAliases LIKE CONCAT('%', :search, '%')
           OR EXISTS (
               SELECT 1 FROM Product p
               WHERE p.brand = b AND p.normalizedName LIKE CONCAT('%', :search, '%')
           )
        ORDER BY
           CASE WHEN b.normalizedName = :search THEN 0
                WHEN b.normalizedName LIKE CONCAT(:search, '%') THEN 1
                ELSE 2 END,
           b.displayName, b.id
        """
    )
    fun searchRanked(search: String, pageable: Pageable): List<Brand>

    /** Every brand as id plus normalized name: the "did you mean" candidate set. */
    @Query("SELECT b.id AS id, b.normalizedName AS normalizedName FROM Brand b")
    fun findAllNames(): List<CatalogName>
}

/** A catalog row reduced to what near-match scoring reads. */
interface CatalogName {
    val id: UUID
    val normalizedName: String
}

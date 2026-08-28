// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.model.BottleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface BottleRepository : JpaRepository<Bottle, UUID> {

    /**
     * The Collection page's listing. Every filter is optional and they compose:
     * a null argument drops that predicate, anything else narrows the result.
     * One query rather than one method per filter, because the previous
     * method-per-filter approach applied only whichever filter matched first.
     *
     * `type` and `status` are the enums, not Strings: Hibernate infers each bind
     * type from the compared column and rejects a String argument at execution.
     *
     * `search` is non-null by design — an empty needle is the "no search" case,
     * since LIKE '%%' matches every row. A null here would reach Postgres as an
     * untyped bind inside LOWER(CONCAT(...)) and fail with
     * "function lower(bytea) does not exist".
     */
    @Query("""
        SELECT b FROM Bottle b JOIN b.product p JOIN p.brand br
        WHERE b.user.id = :userId
        AND (:status IS NULL OR b.status = :status)
        AND (:type IS NULL OR p.type = :type)
        AND (LOWER(p.displayName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(br.displayName) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun findFiltered(
        userId: UUID,
        status: BottleStatus?,
        type: AlcoholType?,
        search: String,
        pageable: Pageable
    ): Page<Bottle>

    fun findByIdAndUserId(id: UUID, userId: UUID): Bottle?

    // --- Statistics queries ---

    fun countByUserId(userId: UUID): Long

    @Query("SELECT COALESCE(SUM(b.purchaseCost), 0) FROM Bottle b WHERE b.user.id = :userId AND b.purchaseCost IS NOT NULL")
    fun sumPurchaseCostByUserId(userId: UUID): BigDecimal

    @Query("SELECT COUNT(b) FROM Bottle b WHERE b.user.id = :userId AND b.purchaseCost IS NOT NULL")
    fun countWithPurchaseCostByUserId(userId: UUID): Long

    @Query("SELECT AVG(CAST(b.rating AS double)) FROM Bottle b WHERE b.user.id = :userId AND b.rating IS NOT NULL")
    fun avgRatingByUserId(userId: UUID): Double?

    @Query("SELECT b.status, COUNT(b) FROM Bottle b WHERE b.user.id = :userId GROUP BY b.status")
    fun countByStatusForUser(userId: UUID): List<Array<Any>>

    @Query("SELECT p.type, COUNT(b) FROM Bottle b JOIN b.product p WHERE b.user.id = :userId GROUP BY p.type ORDER BY COUNT(b) DESC")
    fun countByTypeForUser(userId: UUID): List<Array<Any>>

    @Query("SELECT YEAR(b.purchaseDate), MONTH(b.purchaseDate), SUM(b.purchaseCost) FROM Bottle b WHERE b.user.id = :userId AND b.purchaseDate IS NOT NULL AND b.purchaseCost IS NOT NULL GROUP BY YEAR(b.purchaseDate), MONTH(b.purchaseDate) ORDER BY YEAR(b.purchaseDate), MONTH(b.purchaseDate)")
    fun monthlySpendingByUser(userId: UUID): List<Array<Any>>

    @Query("SELECT b FROM Bottle b JOIN FETCH b.product p JOIN FETCH p.brand WHERE b.user.id = :userId AND b.rating IS NOT NULL ORDER BY b.rating DESC, b.updatedAt DESC")
    fun findTopRatedByUser(userId: UUID, pageable: Pageable): List<Bottle>

    @Query("SELECT b FROM Bottle b JOIN FETCH b.product p JOIN FETCH p.brand WHERE b.user.id = :userId ORDER BY b.purchaseDate DESC NULLS LAST, b.createdAt DESC")
    fun findRecentByUser(userId: UUID, pageable: Pageable): List<Bottle>
}

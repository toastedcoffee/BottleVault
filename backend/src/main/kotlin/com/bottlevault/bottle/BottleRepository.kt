package com.bottlevault.bottle

import com.bottlevault.common.model.BottleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface BottleRepository : JpaRepository<Bottle, UUID> {

    fun findByUserId(userId: UUID, pageable: Pageable): Page<Bottle>

    fun findByUserIdAndStatus(userId: UUID, status: BottleStatus, pageable: Pageable): Page<Bottle>

    @Query("""
        SELECT b FROM Bottle b JOIN b.product p JOIN p.brand br
        WHERE b.user.id = :userId AND p.type = :type
    """)
    fun findByUserIdAndProductType(userId: UUID, type: String, pageable: Pageable): Page<Bottle>

    @Query("""
        SELECT b FROM Bottle b JOIN b.product p JOIN p.brand br
        WHERE b.user.id = :userId
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(br.name) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun searchByUserIdAndQuery(userId: UUID, search: String, pageable: Pageable): Page<Bottle>

    fun findByIdAndUserId(id: UUID, userId: UUID): Bottle?

    // --- Statistics queries ---

    fun countByUserId(userId: UUID): Long

    @Query("SELECT COALESCE(SUM(b.purchaseCost), 0) FROM Bottle b WHERE b.user.id = :userId AND b.purchaseCost IS NOT NULL")
    fun sumPurchaseCostByUserId(userId: UUID): BigDecimal

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

    @Query("SELECT b FROM Bottle b JOIN FETCH b.product p JOIN FETCH p.brand WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    fun findRecentByUser(userId: UUID, pageable: Pageable): List<Bottle>
}

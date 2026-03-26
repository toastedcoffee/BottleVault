package com.bottlevault.bottle

import com.bottlevault.common.model.BottleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}

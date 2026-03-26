package com.bottlevault.product

import com.bottlevault.common.model.AlcoholType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID> {

    fun findByBrandId(brandId: UUID): List<Product>

    fun findByType(type: AlcoholType): List<Product>

    fun findByBarcode(barcode: String): Product?

    @Query("""
        SELECT p FROM Product p JOIN p.brand b
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY b.name, p.name
    """)
    fun search(search: String): List<Product>
}

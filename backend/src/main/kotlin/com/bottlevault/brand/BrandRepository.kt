package com.bottlevault.brand

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BrandRepository : JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY b.name")
    fun searchByName(search: String): List<Brand>

    fun existsByName(name: String): Boolean
}

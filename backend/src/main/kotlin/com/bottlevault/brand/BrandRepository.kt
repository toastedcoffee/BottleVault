// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BrandRepository : JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE LOWER(b.displayName) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY b.displayName")
    fun searchByDisplayName(search: String): List<Brand>

    fun existsByDisplayName(displayName: String): Boolean
}

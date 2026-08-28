// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.common.model.AlcoholType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID> {
    fun findByBrandId(brandId: UUID): List<Product>

    fun findByType(type: AlcoholType): List<Product>

    fun findByBarcode(barcode: String): Product?

    /** Duplicate guard, scoped per brand: two distilleries may both ship a "12 Year Old". */
    fun findByBrandIdAndNormalizedName(brandId: UUID, normalizedName: String): Product?

    @Query(
        """
        SELECT p FROM Product p JOIN p.brand b
        WHERE p.normalizedName LIKE CONCAT('%', :search, '%')
           OR b.normalizedName LIKE CONCAT('%', :search, '%')
           OR b.normalizedAliases LIKE CONCAT('%', :search, '%')
        ORDER BY b.displayName, p.displayName
        """
    )
    fun search(search: String): List<Product>
}

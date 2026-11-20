// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.brand.CatalogName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, ProductRepositoryCustom {

    fun findByBarcode(barcode: String): Product?

    /** Duplicate guard, scoped per brand: two distilleries may both ship a "12 Year Old". */
    fun findByBrandIdAndNormalizedName(brandId: UUID, normalizedName: String): Product?

    /** One brand's products as id plus normalized name: the "did you mean" candidate set. */
    @Query("SELECT p.id AS id, p.normalizedName AS normalizedName FROM Product p WHERE p.brand.id = :brandId")
    fun findNamesByBrandId(brandId: UUID): List<CatalogName>

    /** Rows by id with their brand in the same query, so mapping them costs no extra reads. */
    @Query("SELECT p FROM Product p JOIN FETCH p.brand WHERE p.id IN :ids")
    fun findAllWithBrandByIdIn(ids: Collection<UUID>): List<Product>
}

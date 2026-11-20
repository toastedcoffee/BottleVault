// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.common.model.AlcoholType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

/**
 * The query is assembled from fixed fragments, one per filter that is present, and
 * every value is a bound parameter. Building it rather than writing
 * `(:brandId IS NULL OR …)` keeps a null parameter out of the SQL entirely, which
 * Postgres cannot always type.
 */
class ProductRepositoryImpl : ProductRepositoryCustom {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findFiltered(brandId: UUID?, type: AlcoholType?, search: String?, limit: Int): List<Product> {
        val where = buildList {
            if (brandId != null) add("b.id = :brandId")
            if (type != null) add("p.type = :type")
            if (search != null) add(
                "(p.normalizedName LIKE CONCAT('%', :search, '%')" +
                    " OR b.normalizedName LIKE CONCAT('%', :search, '%')" +
                    " OR b.normalizedAliases LIKE CONCAT('%', :search, '%'))"
            )
        }
        val rank = if (search != null) {
            "CASE WHEN p.normalizedName = :search THEN 0" +
                " WHEN p.normalizedName LIKE CONCAT(:search, '%') THEN 1 ELSE 2 END, "
        } else ""
        val jpql = buildString {
            append("SELECT p FROM Product p JOIN FETCH p.brand b")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(rank).append("b.displayName, p.displayName, p.id")
        }
        val query = entityManager.createQuery(jpql, Product::class.java).setMaxResults(limit)
        if (brandId != null) query.setParameter("brandId", brandId)
        if (type != null) query.setParameter("type", type)
        if (search != null) query.setParameter("search", search)
        return query.resultList
    }
}

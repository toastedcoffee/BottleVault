// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.common.model.AlcoholType
import java.util.UUID

interface ProductRepositoryCustom {
    /**
     * Products matching every non-null filter at once, brand fetched in the same
     * query, at most [limit] rows. With [search], ranked exact product name, then
     * prefix, then the rest; then by brand and product name.
     *
     * [search] must already be normalized (letters, digits and single spaces only).
     */
    fun findFiltered(brandId: UUID?, type: AlcoholType?, search: String?, limit: Int): List<Product>
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.brand.dto.BrandResponse
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.text.NameNormalizer
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BrandService(private val brandRepository: BrandRepository) {

    fun getAllBrands(): List<BrandResponse> =
        brandRepository.findAll(Sort.by("displayName")).map { BrandResponse.from(it) }

    fun getBrandById(id: UUID): BrandResponse {
        val brand = brandRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Brand not found") }
        return BrandResponse.from(brand)
    }

    fun searchBrands(query: String): List<BrandResponse> {
        val normalized = NameNormalizer.normalize(query)
        // A query with no letters or digits (e.g. "&", "...") normalizes to "".
        // LIKE CONCAT('%', :search, '%') on an empty string matches every row,
        // so guard it explicitly rather than dumping the whole catalogue.
        if (normalized.isEmpty()) return emptyList()
        return brandRepository.searchByNormalized(normalized).map { BrandResponse.from(it) }
    }

    /**
     * Creating a brand that normalizes onto an existing one returns that one.
     * From the user's point of view they got the brand they asked for, and the
     * old behavior (throwing ResourceAlreadyExistsException) surfaced an error
     * for something that is not a failure.
     */
    @Transactional
    fun createBrand(request: BrandCreateRequest): BrandResponse {
        val normalized = NameNormalizer.normalize(request.name)
        // @NotBlank on the request only rejects whitespace <= U+0020; a name made
        // entirely of punctuation (or a single U+00A0) passes validation but
        // normalizes to "". Without this guard that row would permanently squat
        // on normalizedName = "", swallow every later punctuation-only submission,
        // and be unreachable via search (which also refuses an empty needle).
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Brand name must contain at least one letter or number")
        }
        brandRepository.findByNormalizedName(normalized)?.let {
            return BrandResponse.from(it)
        }

        val brand = Brand(
            displayName = NameNormalizer.displayName(request.name),
            normalizedName = normalized,
            country = request.country,
            website = request.website,
        )
        return BrandResponse.from(brandRepository.save(brand))
    }
}

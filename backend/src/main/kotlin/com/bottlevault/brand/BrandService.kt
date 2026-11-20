// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.brand

import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.brand.dto.BrandResponse
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.text.CatalogLimits
import com.bottlevault.common.text.NameMatcher
import com.bottlevault.common.text.NameNormalizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BrandService(private val brandRepository: BrandRepository) {

    fun getBrandById(id: UUID): BrandResponse {
        val brand = brandRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Brand not found") }
        return BrandResponse.from(brand)
    }

    /**
     * At most [CatalogLimits.LIST_MAX] brands, exact match first. A term is
     * required (spec H5): with none the whole catalog would come back. A term that
     * normalizes to nothing ("&", "...") counts as none, since LIKE '%%' matches
     * every row.
     */
    fun searchBrands(query: String?): List<BrandResponse> {
        val normalized = NameNormalizer.normalize(query.orEmpty())
        require(normalized.length >= CatalogLimits.MIN_LIST_TERM) {
            "Search term must be at least ${CatalogLimits.MIN_LIST_TERM} letters or digits"
        }
        return brandRepository.searchRanked(normalized, PageRequest.of(0, CatalogLimits.LIST_MAX))
            .map { BrandResponse.from(it) }
    }

    /**
     * "Did you mean": brands whose name is near [name] but not the same name.
     * Scored in memory over every brand's normalized name. No cache, because the
     * catalog review script changes rows behind the app's back.
     */
    fun suggestBrands(name: String?): List<BrandResponse> {
        val normalized = NameNormalizer.normalize(name.orEmpty())
        if (normalized.length < CatalogLimits.MIN_SUGGEST_TERM) return emptyList()
        val candidates = brandRepository.findAllNames().map { NameMatcher.Candidate(it.id, it.normalizedName) }
        val ids = NameMatcher.rank(normalized, candidates, CatalogLimits.SUGGEST_MAX)
        if (ids.isEmpty()) return emptyList()
        val byId = brandRepository.findAllById(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }.map { BrandResponse.from(it) }
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
        CatalogLimits.requireNormalizedFits(normalized, "Brand")
        brandRepository.findByNormalizedName(normalized)?.let {
            return BrandResponse.from(it)
        }

        val brand = Brand(
            displayName = NameNormalizer.displayName(request.name),
            normalizedName = normalized,
            country = request.country,
        )
        // The lookup above is not atomic with this insert: a concurrent create of the
        // same name can land in between, and the unique constraint then refuses this
        // one. That is a conflict, not a server fault. saveAndFlush, not save, so the
        // violation surfaces here rather than at commit, outside the try.
        val saved = try {
            brandRepository.saveAndFlush(brand)
        } catch (e: DataIntegrityViolationException) {
            throw ResourceAlreadyExistsException("This brand was just added. Search for it and pick it.")
        }
        return BrandResponse.from(saved)
    }
}

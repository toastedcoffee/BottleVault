// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.CatalogLimits
import com.bottlevault.common.text.NameMatcher
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.product.dto.ProductResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository
) {
    /**
     * At most [CatalogLimits.LIST_MAX] products matching every filter given. Spec H5:
     * a list needs either a [brandId] (one brand's products, optionally narrowed by
     * a term) or a term of at least [CatalogLimits.MIN_LIST_TERM] normalized
     * characters. [type] narrows either but is not enough alone.
     */
    fun getProducts(brandId: UUID?, type: AlcoholType?, search: String?): List<ProductResponse> {
        // A term that normalizes to nothing ("&", "...") counts as no term: LIKE '%%'
        // would match every row.
        val normalized = search?.let { NameNormalizer.normalize(it) }?.takeIf { it.isNotEmpty() }
        if (brandId == null) {
            require(normalized != null && normalized.length >= CatalogLimits.MIN_LIST_TERM) {
                "Give a brandId or a search term of at least ${CatalogLimits.MIN_LIST_TERM} letters or digits"
            }
        }
        return productRepository.findFiltered(brandId, type, normalized, CatalogLimits.LIST_MAX)
            .map { ProductResponse.from(it) }
    }

    /**
     * "Did you mean": products of [brandId] whose name is near [name] but not the
     * same name. Scored in memory over that brand's normalized names; no cache,
     * because the catalog review script changes rows behind the app's back.
     */
    fun suggestProducts(brandId: UUID, name: String?): List<ProductResponse> {
        val normalized = NameNormalizer.normalize(name.orEmpty())
        if (normalized.length < CatalogLimits.MIN_SUGGEST_TERM) return emptyList()
        val candidates = productRepository.findNamesByBrandId(brandId)
            .map { NameMatcher.Candidate(it.id, it.normalizedName) }
        val ids = NameMatcher.rank(normalized, candidates, CatalogLimits.SUGGEST_MAX)
        if (ids.isEmpty()) return emptyList()
        val byId = productRepository.findAllWithBrandByIdIn(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }.map { ProductResponse.from(it) }
    }

    fun getProductById(id: UUID): ProductResponse {
        val product = productRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Product not found") }
        return ProductResponse.from(product)
    }

    fun getProductByBarcode(barcode: String): ProductResponse? {
        val product = productRepository.findByBarcode(barcode) ?: return null
        return ProductResponse.from(product)
    }

    /**
     * Creating a product that normalizes onto an existing one (within the same
     * brand) returns that one. From the user's point of view they got the
     * product they asked for, and throwing here would surface an error for
     * something that is not a failure.
     */
    @Transactional
    fun createProduct(request: ProductCreateRequest): ProductResponse {
        val brand = brandRepository.findById(UUID.fromString(request.brandId))
            .orElseThrow { ResourceNotFoundException("Brand not found") }
        // Blank means "no barcode" on both paths. Stored as "" it would claim the
        // globally unique column, and the next blank submission would fail as a 500.
        val submittedBarcode = request.barcode?.takeIf { it.isNotBlank() }

        val normalized = NameNormalizer.normalize(request.name)
        // @NotBlank on the request only rejects whitespace <= U+0020; a name made
        // entirely of punctuation (or a single U+00A0) passes validation but
        // normalizes to "". Without this guard that row would permanently squat
        // on normalizedName = "" for this brand, swallow every later
        // punctuation-only submission, and be unreachable via search (which also
        // refuses an empty needle).
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Product name must contain at least one letter or number")
        }
        CatalogLimits.requireNormalizedFits(normalized, "Product")
        productRepository.findByBrandIdAndNormalizedName(brand.id!!, normalized)?.let { existing ->
            // The find-or-create above can match a product that predates barcode
            // scanning (or was created by hand) and so has no barcode yet. 32 of 34
            // products in the production catalogue arrived via barcode scan, so
            // discarding a submitted barcode here would mean that same bottle
            // permanently misses local lookup and re-hits the external provider on
            // every future scan. Only fill a gap - a product that already has a
            // barcode keeps it; the submitted one is not a correction.
            if (existing.barcode == null && submittedBarcode != null) {
                // Product.barcode is globally unique, not scoped to this brand/product.
                // If the submitted barcode already belongs to a different row, saving
                // it here would violate that constraint. Surface it as a 409 - the
                // same status the old pre-normalization duplicate-name path used -
                // rather than swallowing the conflict (which would leave the scan
                // silently unresolved) or letting the DataIntegrityViolationException
                // bubble up as an unmapped 500.
                rejectBarcodeHeldElsewhere(submittedBarcode)
                existing.barcode = submittedBarcode
                // The lookup above is not atomic with this save: two concurrent scans
                // of the same barcode can both find nothing and both try to claim it.
                // The database's unique constraint is what actually prevents the
                // duplicate, but it surfaces as a DataIntegrityViolationException,
                // which maps to 500. Translate it to the same 409 the check above
                // produces, so the loser of the race gets the correct answer rather
                // than an unmapped server error.
                try {
                    productRepository.saveAndFlush(existing)
                } catch (e: DataIntegrityViolationException) {
                    throw ResourceAlreadyExistsException(barcodeTaken(submittedBarcode))
                }
            }
            return ProductResponse.from(existing)
        }

        // The same conflict on the new-product path: a barcode already on another
        // product is a 409, not a unique-constraint 500.
        submittedBarcode?.let { rejectBarcodeHeldElsewhere(it) }
        val product = Product(
            brand = brand,
            displayName = NameNormalizer.displayName(request.name),
            normalizedName = normalized,
            barcode = submittedBarcode,
            type = request.type,
            subtype = request.subtype,
            size = request.size,
            abv = request.abv,
            isUserCreated = true
        )
        // Neither check above is atomic with the insert. A concurrent create of the
        // same name in this brand, or of the same barcode, is refused by a unique
        // constraint; translate it to a 409 here (saveAndFlush, so it fires inside
        // the try) rather than let it surface as an unmapped 500. Which constraint
        // fired is not re-queried: after a failed flush the transaction is aborted.
        val saved = try {
            productRepository.saveAndFlush(product)
        } catch (e: DataIntegrityViolationException) {
            throw ResourceAlreadyExistsException(
                "This product or its barcode was just added. Search for it and pick it."
            )
        }
        return ProductResponse.from(saved)
    }

    private fun rejectBarcodeHeldElsewhere(barcode: String) {
        productRepository.findByBarcode(barcode)?.let {
            throw ResourceAlreadyExistsException(barcodeTaken(barcode))
        }
    }

    private fun barcodeTaken(barcode: String) = "Barcode $barcode is already registered to a different product"
}

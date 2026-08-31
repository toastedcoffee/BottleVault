// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.product

import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.model.AlcoholType
import com.bottlevault.common.text.NameNormalizer
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.product.dto.ProductResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository
) {
    fun getProducts(brandId: UUID?, type: AlcoholType?, search: String?): List<ProductResponse> {
        val products = when {
            !search.isNullOrBlank() -> {
                val normalized = NameNormalizer.normalize(search)
                // A query with no letters or digits (e.g. "&", "...") normalizes to "".
                // LIKE CONCAT('%', :search, '%') on an empty string matches every row,
                // so guard it explicitly rather than dumping the whole catalogue.
                if (normalized.isEmpty()) emptyList() else productRepository.search(normalized)
            }
            brandId != null -> productRepository.findByBrandId(brandId)
            type != null -> productRepository.findByType(type)
            else -> productRepository.findAll()
        }
        return products.map { ProductResponse.from(it) }
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
        productRepository.findByBrandIdAndNormalizedName(brand.id!!, normalized)?.let { existing ->
            // The find-or-create above can match a product that predates barcode
            // scanning (or was created by hand) and so has no barcode yet. 32 of 34
            // products in the production catalogue arrived via barcode scan, so
            // discarding a submitted barcode here would mean that same bottle
            // permanently misses local lookup and re-hits the external provider on
            // every future scan. Only fill a gap - a product that already has a
            // barcode keeps it; the submitted one is not a correction.
            val submittedBarcode = request.barcode?.takeIf { it.isNotBlank() }
            if (existing.barcode == null && submittedBarcode != null) {
                // Product.barcode is globally unique, not scoped to this brand/product.
                // If the submitted barcode already belongs to a different row, saving
                // it here would violate that constraint. Surface it as a 409 - the
                // same status the old pre-normalization duplicate-name path used -
                // rather than swallowing the conflict (which would leave the scan
                // silently unresolved) or letting the DataIntegrityViolationException
                // bubble up as an unmapped 500.
                productRepository.findByBarcode(submittedBarcode)?.let {
                    throw ResourceAlreadyExistsException(
                        "Barcode $submittedBarcode is already registered to a different product"
                    )
                }
                existing.barcode = submittedBarcode
                productRepository.save(existing)
            }
            return ProductResponse.from(existing)
        }

        val product = Product(
            brand = brand,
            displayName = NameNormalizer.displayName(request.name),
            normalizedName = normalized,
            barcode = request.barcode,
            type = request.type,
            subtype = request.subtype,
            size = request.size,
            abv = request.abv,
            description = request.description,
            imageUrl = request.imageUrl,
            isUserCreated = true
        )
        return ProductResponse.from(productRepository.save(product))
    }
}

package com.bottlevault.product

import com.bottlevault.brand.BrandRepository
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.model.AlcoholType
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
            !search.isNullOrBlank() -> productRepository.search(search)
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

    @Transactional
    fun createProduct(request: ProductCreateRequest): ProductResponse {
        val brand = brandRepository.findById(UUID.fromString(request.brandId))
            .orElseThrow { ResourceNotFoundException("Brand not found") }

        val product = Product(
            brand = brand,
            name = request.name,
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

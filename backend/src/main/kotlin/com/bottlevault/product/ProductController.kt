package com.bottlevault.product

import com.bottlevault.common.model.AlcoholType
import com.bottlevault.product.dto.ProductCreateRequest
import com.bottlevault.product.dto.ProductResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/products")
class ProductController(private val productService: ProductService) {

    @GetMapping
    fun getProducts(
        @RequestParam brandId: UUID?,
        @RequestParam type: AlcoholType?,
        @RequestParam search: String?
    ): List<ProductResponse> =
        productService.getProducts(brandId, type, search)

    @GetMapping("/{id}")
    fun getProductById(@PathVariable id: UUID): ProductResponse =
        productService.getProductById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@Valid @RequestBody request: ProductCreateRequest): ProductResponse =
        productService.createProduct(request)
}

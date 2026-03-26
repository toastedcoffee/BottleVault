package com.bottlevault.brand

import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.brand.dto.BrandResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/brands")
class BrandController(private val brandService: BrandService) {

    @GetMapping
    fun getAllBrands(@RequestParam search: String?): List<BrandResponse> =
        if (search.isNullOrBlank()) brandService.getAllBrands()
        else brandService.searchBrands(search)

    @GetMapping("/{id}")
    fun getBrandById(@PathVariable id: UUID): BrandResponse =
        brandService.getBrandById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBrand(@Valid @RequestBody request: BrandCreateRequest): BrandResponse =
        brandService.createBrand(request)
}

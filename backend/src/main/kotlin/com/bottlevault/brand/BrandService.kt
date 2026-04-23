package com.bottlevault.brand

import com.bottlevault.brand.dto.BrandCreateRequest
import com.bottlevault.brand.dto.BrandResponse
import com.bottlevault.common.exception.ResourceAlreadyExistsException
import com.bottlevault.common.exception.ResourceNotFoundException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BrandService(private val brandRepository: BrandRepository) {

    fun getAllBrands(): List<BrandResponse> =
        brandRepository.findAll(Sort.by("name")).map { BrandResponse.from(it) }

    fun getBrandById(id: UUID): BrandResponse {
        val brand = brandRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Brand not found") }
        return BrandResponse.from(brand)
    }

    fun searchBrands(query: String): List<BrandResponse> =
        brandRepository.searchByName(query).map { BrandResponse.from(it) }

    @Transactional
    fun createBrand(request: BrandCreateRequest): BrandResponse {
        if (brandRepository.existsByName(request.name)) {
            throw ResourceAlreadyExistsException("Brand '${request.name}' already exists")
        }
        val brand = Brand(
            name = request.name,
            country = request.country,
            website = request.website
        )
        return BrandResponse.from(brandRepository.save(brand))
    }
}

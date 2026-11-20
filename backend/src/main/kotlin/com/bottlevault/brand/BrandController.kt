// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
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

    /** A bounded, ranked search. There is no unfiltered list: a missing or one-letter term is a 400. */
    @GetMapping
    fun searchBrands(@RequestParam search: String?): List<BrandResponse> =
        brandService.searchBrands(search)

    /** "Did you mean": up to five brands near [name], never the same name. Under 3 characters: none. */
    @GetMapping("/suggest")
    fun suggestBrands(@RequestParam name: String?): List<BrandResponse> =
        brandService.suggestBrands(name)

    @GetMapping("/{id}")
    fun getBrandById(@PathVariable id: UUID): BrandResponse =
        brandService.getBrandById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBrand(@Valid @RequestBody request: BrandCreateRequest): BrandResponse =
        brandService.createBrand(request)
}

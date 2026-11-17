// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.barcode

import com.bottlevault.barcode.dto.BarcodeLookupResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/barcode")
class BarcodeController(private val barcodeService: BarcodeService) {

    @GetMapping("/{barcode}")
    fun lookupBarcode(@PathVariable barcode: String): BarcodeLookupResponse {
        // UPC-E/EAN-8 through GTIN-14. Anything else never reaches a provider or
        // the miss cache, so junk can grow neither.
        require(BARCODE.matches(barcode)) { "That doesn't look like a product barcode (8 to 14 digits)" }
        return barcodeService.lookupBarcode(barcode)
    }

    private companion object {
        val BARCODE = Regex("^[0-9]{8,14}$")
    }
}

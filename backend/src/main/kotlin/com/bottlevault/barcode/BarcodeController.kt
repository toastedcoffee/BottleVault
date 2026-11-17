// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.barcode

import com.bottlevault.barcode.dto.BarcodeLookupResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/barcode")
class BarcodeController(private val barcodeService: BarcodeService) {

    @GetMapping("/{barcode}")
    fun lookupBarcode(@PathVariable barcode: String, auth: Authentication): BarcodeLookupResponse {
        // UPC-E/EAN-8 through GTIN-14. Anything else never reaches a provider or
        // the miss cache, so junk can grow neither.
        require(BARCODE.matches(barcode)) { "That doesn't look like a product barcode (8 to 14 digits)" }
        // The principal is the user id as a String (JwtAuthenticationFilter). If it
        // ever becomes a UUID, this cast fails at runtime, not at compile time.
        return barcodeService.lookupBarcode(barcode, UUID.fromString(auth.principal as String))
    }

    private companion object {
        val BARCODE = Regex("^[0-9]{8,14}$")
    }
}

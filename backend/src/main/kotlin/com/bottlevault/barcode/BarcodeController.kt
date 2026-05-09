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
    fun lookupBarcode(@PathVariable barcode: String): BarcodeLookupResponse =
        barcodeService.lookupBarcode(barcode)
}

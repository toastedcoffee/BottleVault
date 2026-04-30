package com.bottlevault.barcode

import com.bottlevault.barcode.dto.ExternalProductData

interface BarcodeProvider {
    val source: String
    val priority: Int
    val isEnabled: Boolean

    fun lookup(barcode: String): ExternalProductData?
}

internal object AbvParser {
    private val numeric = Regex("[0-9]+(?:\\.[0-9]+)?")

    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return numeric.find(raw)?.value?.toDoubleOrNull()
    }
}

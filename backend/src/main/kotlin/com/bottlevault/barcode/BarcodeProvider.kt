package com.bottlevault.barcode

import com.bottlevault.barcode.dto.ExternalProductData

interface BarcodeProvider {
    val source: String
    val priority: Int
    val isEnabled: Boolean

    fun lookup(barcode: String): ExternalProductData?
}

internal object AbvParser {
    /** Numeric ABV value in [0, 100]. For structured fields (e.g. Open Food Facts'
     *  `alcohol_by_volume`) that come back as a clean numeric string. */
    private val plainNumber = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*$")

    /** Percent pattern requiring nearby alcohol-related context, in either order:
     *    "40% ABV", "5.0% alc", "12 % vol"
     *    "ABV 40%", "alc. 5.0%", "alcohol 12.5%"
     *  Bare "100%" (e.g. "100% natural", "100% pure") will NOT match. This is intentional:
     *  external APIs return so much marketing copy that bare percents are unreliable. We'd
     *  rather have the user fill in ABV than auto-populate it with garbage. */
    private val percentAfter = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:abv|alc(?:\\.|ohol)?|vol\\.?)",
        RegexOption.IGNORE_CASE
    )
    private val percentBefore = Regex(
        "(?:abv|alc(?:\\.|ohol)?|alcohol\\s+by\\s+volume)[^\\d]{0,8}(\\d+(?:\\.\\d+)?)\\s*%",
        RegexOption.IGNORE_CASE
    )

    /** For structured numeric fields. Returns the value if it parses and is in [0, 100]. */
    fun parseNumeric(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val v = plainNumber.matchEntire(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return v.takeIf { it in 0.0..100.0 }
    }

    /** For free-form titles/descriptions. Requires the number to be adjacent to an
     *  alcohol-related keyword (ABV / alc / alcohol / vol). Returns the first plausible
     *  match in [0, 100]. */
    fun parsePercent(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val matches = percentAfter.findAll(raw).map { it.groupValues[1] } +
            percentBefore.findAll(raw).map { it.groupValues[1] }
        return matches
            .mapNotNull { it.toDoubleOrNull() }
            .firstOrNull { it in 0.0..100.0 }
    }
}

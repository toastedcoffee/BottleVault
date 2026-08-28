// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.text

import java.text.Normalizer as UnicodeNormalizer

/**
 * The single source of truth for brand and product name normalization.
 *
 * Two derived forms:
 *  - [displayName] is what a human sees. Trimmed and whitespace-collapsed only,
 *    because case, punctuation and diacritics all carry meaning: BenRiach,
 *    Jack Dan'iels, Patrón.
 *  - [normalize] is the matching key backing the UNIQUE constraint and every
 *    duplicate check. Aggressive by design.
 *
 * This runs in Kotlin rather than SQL because diacritic folding has no portable
 * SQL form: `unaccent` is Postgres-only and would break the H2 dev profile. The
 * V8 Flyway migration calls this same object, so the backfill cannot drift from
 * runtime behavior.
 */
object NameNormalizer {

    /**
     * Unicode whitespace, not just ASCII. Kotlin's `\s` does not match U+00A0
     * (non-breaking space) or U+200B (zero-width space); a name carrying one
     * would otherwise normalize with an invisible character still in it and
     * collide with nothing, defeating the UNIQUE constraint while looking fine.
     */
    private val WHITESPACE = Regex("""[\s  ᠎ -​    　]+""")

    /** Anything that is not a letter, a digit, or a single space. */
    private val NON_ALPHANUMERIC = Regex("""[^\p{IsAlphabetic}\p{IsDigit} ]""")

    /** Combining marks left behind by NFD decomposition. */
    private val COMBINING_MARKS = Regex("""\p{InCombiningDiacriticalMarks}+""")

    fun displayName(raw: String): String =
        WHITESPACE.replace(raw, " ").trim()

    fun normalize(raw: String): String {
        val collapsed = displayName(raw)
        val folded = COMBINING_MARKS.replace(
            UnicodeNormalizer.normalize(collapsed, UnicodeNormalizer.Form.NFD),
            "",
        )
        val stripped = NON_ALPHANUMERIC.replace(folded, "")
        // Stripping punctuation can leave a double space ("Brewery & Kitchen"),
        // so collapse a second time before lowercasing.
        return WHITESPACE.replace(stripped, " ").trim().lowercase()
    }
}

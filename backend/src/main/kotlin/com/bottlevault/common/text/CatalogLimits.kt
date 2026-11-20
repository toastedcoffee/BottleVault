// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.text

/**
 * Bounds shared by the brand and product catalog endpoints (spec H3 and H5).
 *
 * The request-field caps live on the DTOs as bean-validation annotations, so a
 * violation answers with the field's name. These are the values the services
 * check themselves.
 */
object CatalogLimits {
    /** Width of `display_name` and `normalized_name` on both catalog tables. */
    const val NAME_MAX = 255

    /** A list request without a `brandId` needs a term at least this long once normalized. */
    const val MIN_LIST_TERM = 2

    /** No catalog list returns more rows than this. */
    const val LIST_MAX = 20

    /** A suggestion term shorter than this (normalized) suggests nothing. */
    const val MIN_SUGGEST_TERM = 3

    /** At most this many "did you mean" rows. */
    const val SUGGEST_MAX = 5

    /**
     * The normalized form is usually no longer than the display form, but NFD can
     * lengthen it: a Hangul syllable decomposes into two or three jamo, which are
     * letters and survive the strip. A display name inside its 255-character cap can
     * therefore normalize past the column width, which Postgres refuses as a 500.
     */
    fun requireNormalizedFits(normalized: String, what: String) {
        require(normalized.length <= NAME_MAX) { "$what name is too long" }
    }
}

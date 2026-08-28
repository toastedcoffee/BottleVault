// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NameNormalizerTest {

    @Test
    fun `displayName trims and collapses whitespace but preserves case and punctuation`() {
        assertEquals("Kirkland Signature", NameNormalizer.displayName("Kirkland Signature "))
        assertEquals("Master's Select", NameNormalizer.displayName("Master's  Select"))
        assertEquals("BenRiach", NameNormalizer.displayName("  BenRiach  "))
        assertEquals("Patrón", NameNormalizer.displayName("Patrón"))
    }

    @Test
    fun `displayName collapses non-breaking and zero-width whitespace`() {
        // WARNING: the three strings below contain LITERAL U+00A0 (non-breaking
        // space) and U+200B (zero-width space) characters, which render
        // identically to an ordinary space. Do not "tidy" them, and do not
        // retype these lines by hand: replacing them with plain spaces turns
        // this into a test that passes trivially and proves nothing. If your
        // editor strips them, rewrite as   and ​ escapes instead.
        // Verify with: grep -P ' ' on the saved file.
        assertEquals("Wilderness Trail", NameNormalizer.displayName("Wilderness Trail"))
        assertEquals("Wilderness Trail", NameNormalizer.displayName("Wilderness Trail​"))
        assertEquals("Wilderness Trail", NameNormalizer.displayName(" Wilderness Trail "))
    }

    @Test
    fun `normalize folds case`() {
        assertEquals("woodinville", NameNormalizer.normalize("WOODINVILLE"))
        assertEquals("jameson", NameNormalizer.normalize("Jameson"))
        assertEquals(NameNormalizer.normalize("jack daniels"), NameNormalizer.normalize("JACK DANIELS"))
    }

    @Test
    fun `normalize strips punctuation so apostrophe variants collide`() {
        assertEquals("jack daniels", NameNormalizer.normalize("Jack Daniel's"))
        assertEquals("jack daniels", NameNormalizer.normalize("jack daniels"))
        assertEquals("titos", NameNormalizer.normalize("Tito's"))
    }

    @Test
    fun `normalize re-collapses whitespace left behind by stripped punctuation`() {
        // The ampersand is surrounded by spaces; removing it must not leave a double space.
        assertEquals(
            "here today brewery kitchen",
            NameNormalizer.normalize("Here Today Brewery & Kitchen"),
        )
        assertEquals(
            "here today brewery kitchen",
            NameNormalizer.normalize("Here Today Brewery& Kitchen"),
        )
    }

    @Test
    fun `normalize folds diacritics`() {
        assertEquals("patron", NameNormalizer.normalize("Patrón"))
        assertEquals("remy martin", NameNormalizer.normalize("Rémy Martin"))
        assertEquals(NameNormalizer.normalize("Patron"), NameNormalizer.normalize("Patrón"))
    }

    @Test
    fun `normalize is idempotent`() {
        val once = NameNormalizer.normalize("  Here Today Brewery & Kitchen  ")
        assertEquals(once, NameNormalizer.normalize(once))
    }

    @Test
    fun `normalize returns empty string for input with no alphanumerics`() {
        assertEquals("", NameNormalizer.normalize("   "))
        assertEquals("", NameNormalizer.normalize("!!!"))
    }
}

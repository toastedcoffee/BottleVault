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
        // WARNING: the three string literals in this test (below) contain REAL,
        // literal instances of two invisible Unicode characters: U+00A0 (NO-BREAK
        // SPACE) and U+200B (ZERO WIDTH SPACE). Both render identically to an
        // ordinary space in most editors and terminals. Do not retype these lines
        // by hand and do not "clean up" whitespace inside the quoted strings --
        // swapping either character for a plain ASCII space silently turns this
        // test into a tautology (a string compared to itself) without failing.
        // This comment names the characters by codepoint instead of embedding one,
        // because an embedded literal is exactly what a future edit can flatten to
        // a plain space -- that already happened once to an earlier draft of this
        // very comment. To confirm the literals are still intact after any edit,
        // count their UTF-8 byte sequences directly (this command has no special
        // characters of its own -- the byte values are written out as decimal,
        // not typed as escapes or literals) from the repo root:
        //   python -c "d=open('backend/src/test/kotlin/com/bottlevault/common/text/NameNormalizerTest.kt','rb').read(); print(d.count(bytes([194,160])), d.count(bytes([226,128,139])))"
        // Expect two nonzero counts: occurrences of U+00A0 (UTF-8 bytes 194,160),
        // then of U+200B (UTF-8 bytes 226,128,139). A zero means the literal was
        // lost -- restore it by name and codepoint, never by copy-pasting from
        // this comment.
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

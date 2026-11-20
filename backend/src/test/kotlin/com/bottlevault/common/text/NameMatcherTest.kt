// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class NameMatcherTest {

    private fun key(raw: String) = NameMatcher.matchKey(NameNormalizer.normalize(raw))

    @ParameterizedTest(name = "leading article \"{0}\" is dropped")
    @MethodSource("articles")
    fun `each leading article is dropped`(article: String) {
        assertEquals("macallan", key("$article Macallan"))
    }

    @ParameterizedTest(name = "producer suffix \"{0}\" is dropped")
    @MethodSource("suffixes")
    fun `each producer suffix is dropped`(suffix: String) {
        assertEquals("sierra nevada", key("Sierra Nevada $suffix"))
    }

    @Test
    fun `suffixes are written with punctuation as users type them`() {
        assertEquals("sierra nevada", key("Sierra Nevada Brewing Co."))
        assertEquals("heaven hill", key("Heaven Hill Distilling Co."))
        assertEquals("sierra nevada", key("Sierra Nevada Brewing & Co"))
    }

    @Test
    fun `the longest suffix is taken whole`() {
        assertEquals("sierra nevada", key("Sierra Nevada Brewing Company"))
    }

    @Test
    fun `only one article and one suffix come off`() {
        assertEquals("the macallan", key("The The Macallan"))
        assertEquals("acme brewing", key("Acme Brewing Brewery"))
    }

    @Test
    fun `a suffix must be a whole word`() {
        assertEquals("acmebrewery", key("Acmebrewery"))
        assertEquals("theodore", key("Theodore"))
    }

    @Test
    fun `a name that is only an article or only a suffix keeps it, so a key is never empty`() {
        assertEquals("the", key("The"))
        assertEquals("brewery", key("Brewery"))
        assertEquals("brewing co", key("Brewing Co"))
        assertEquals("brewery", key("The Brewery"))
    }

    @Test
    fun `threshold is 1 up to 8 characters, 2 up to 16 and 3 beyond, by the longer key`() {
        assertEquals(1, NameMatcher.threshold("a".repeat(8), "a".repeat(3)))
        assertEquals(2, NameMatcher.threshold("a".repeat(9), "a"))
        assertEquals(2, NameMatcher.threshold("a".repeat(16), "a"))
        assertEquals(3, NameMatcher.threshold("a".repeat(17), "a"))
    }

    @Test
    fun `distance is exact within the cap and capped beyond it`() {
        assertEquals(0, NameMatcher.distance("macallan", "macallan", 1))
        assertEquals(1, NameMatcher.distance("macallan", "macalan", 1))
        assertEquals(3, NameMatcher.distance("kitten", "sitting", 3))
        assertEquals(2, NameMatcher.distance("kitten", "sitting", 1), "beyond the cap it reports max + 1")
        assertEquals(4, NameMatcher.distance("abc", "abcdefgh", 3), "a length gap alone exceeds the cap")
    }

    @Test
    fun `a one-letter typo is near and an unrelated name is not`() {
        assertTrue(NameMatcher.isNear(key("Wilderness tlTrail"), key("Wilderness Trail")))
        assertTrue(NameMatcher.isNear(key("Macalan"), key("Macallan")))
        assertFalse(NameMatcher.isNear(key("Buffalo Trace"), key("Macallan")))
    }

    @Test
    fun `rank never returns the exact name, orders nearest first and stops at the limit`() {
        val candidates = listOf("macallan", "the macallan", "macallen", "glenfiddich", "macalan")
            .mapIndexed { i, n -> NameMatcher.Candidate(i, n) }
        val ranked = NameMatcher.rank("macallan", candidates, 5)
        assertFalse(0 in ranked, "the exact normalized name is not a suggestion")
        assertEquals(1, ranked.first(), "the article-only difference is distance 0")
        assertEquals(setOf(1, 2, 4), ranked.toSet())
        assertEquals(2, NameMatcher.rank("macallan", candidates, 2).size)
    }

    companion object {
        @JvmStatic
        fun articles() = NameMatcher.ARTICLES

        @JvmStatic
        fun suffixes() = NameMatcher.PRODUCER_SUFFIXES
    }
}

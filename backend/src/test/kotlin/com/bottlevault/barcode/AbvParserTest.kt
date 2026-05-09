package com.bottlevault.barcode

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbvParserTest {

    // --- parsePercent: free-form titles & descriptions ---

    @Test
    fun `parsePercent extracts ABV from percent ABV suffix`() {
        assertEquals(40.0, AbvParser.parsePercent("Old No. 7 Tennessee Whiskey 40% ABV"))
    }

    @Test
    fun `parsePercent extracts decimal ABV`() {
        assertEquals(43.5, AbvParser.parsePercent("Bourbon, 43.5% alc"))
    }

    @Test
    fun `parsePercent ignores numbers without percent sign`() {
        // "12 fl oz" must not be parsed as 12% ABV
        assertNull(AbvParser.parsePercent("Sprite Lemon Lime Soda Soft Drinks, 12 fl oz, 12 Pack"))
    }

    @Test
    fun `parsePercent ignores model numbers`() {
        // The original bug: "Lenox 874230 13 Federal Platinum" returning 874230 as ABV
        assertNull(AbvParser.parsePercent("Lenox 874230 13 Federal Platinum Mono Block Dinnerware"))
    }

    @Test
    fun `parsePercent ignores bare percent without alcohol context`() {
        // The Sprite bug: descriptions full of "100% natural" or "50% recycled".
        assertNull(AbvParser.parsePercent("100% natural flavors, no preservatives"))
        assertNull(AbvParser.parsePercent("Made with 100% pure cane sugar"))
        assertNull(AbvParser.parsePercent("Save 50% off retail"))
    }

    @Test
    fun `parsePercent matches keyword-prefix form`() {
        assertEquals(40.0, AbvParser.parsePercent("Bourbon, ABV 40%"))
        assertEquals(12.5, AbvParser.parsePercent("Wine - alcohol 12.5%"))
        assertEquals(5.0, AbvParser.parsePercent("Beer (alc. 5%)"))
    }

    @Test
    fun `parsePercent skips out-of-range values and finds the next match`() {
        // ">100% ABV" is impossible; parser should reject and look further.
        assertEquals(40.0, AbvParser.parsePercent("Sale 200% off ABV 40% alc"))
    }

    @Test
    fun `parsePercent returns null for null or blank`() {
        assertNull(AbvParser.parsePercent(null))
        assertNull(AbvParser.parsePercent(""))
        assertNull(AbvParser.parsePercent("   "))
    }

    // --- parseNumeric: structured fields like OFF's alcohol_by_volume ---

    @Test
    fun `parseNumeric handles clean integer string`() {
        assertEquals(40.0, AbvParser.parseNumeric("40"))
    }

    @Test
    fun `parseNumeric handles decimal string`() {
        assertEquals(12.5, AbvParser.parseNumeric("12.5"))
    }

    @Test
    fun `parseNumeric tolerates surrounding whitespace`() {
        assertEquals(5.0, AbvParser.parseNumeric("  5  "))
    }

    @Test
    fun `parseNumeric rejects non-numeric content`() {
        assertNull(AbvParser.parseNumeric("40% ABV"))
        assertNull(AbvParser.parseNumeric("forty"))
    }

    @Test
    fun `parseNumeric rejects out-of-range values`() {
        assertNull(AbvParser.parseNumeric("150"))
        assertNull(AbvParser.parseNumeric("-5"))
    }

    @Test
    fun `parseNumeric returns null for null or blank`() {
        assertNull(AbvParser.parseNumeric(null))
        assertNull(AbvParser.parseNumeric(""))
    }
}

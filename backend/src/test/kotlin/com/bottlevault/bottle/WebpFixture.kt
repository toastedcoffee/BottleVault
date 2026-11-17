// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import java.util.Base64

object WebpFixture {
    /**
     * A 2x2 lossless WebP (red, green, blue, white), 52 bytes. Encoded with
     * Pillow 12.2 (`Image.save(..., "WEBP", lossless=True)`) and verified to
     * decode back to 2x2. Proves the WebP reader is on the classpath.
     */
    fun lossless2x2(): ByteArray = Base64.getDecoder().decode(ENCODED)

    /**
     * An 8x8 pure-red CMYK JPEG with an Adobe marker, 353 bytes, from Pillow 12.2
     * (`Image.new("CMYK", (8, 8), (0, 255, 255, 0)).save(..., "JPEG", quality=90)`).
     * The JDK's built-in JPEG reader cannot decode CMYK; this proves the
     * TwelveMonkeys JPEG plugin is doing it. (Kept here with the other
     * encoder-generated fixture.)
     */
    fun cmykJpeg8x8(): ByteArray = Base64.getDecoder().decode(CMYK)

    private const val ENCODED = "UklGRiwAAABXRUJQVlA4TB8AAAAvAUAAAB8gEEjeHzqN+RcQFPwf3fxHZA/gBgwR/Q8BAA=="

    private const val CMYK =
        "/9j/7gAOQWRvYmUAZAAAAAAA/9sAQwADAgIDAgIDAwMDBAMDBAUIBQUEBAUKBwcGCAwKDAwLCgsLDQ4SEA0OEQ4LCxAWEBETFBUVFQwPFxgWFBgSFBUU" +
            "/8AAFAgACAAIBEMRAE0RAFkRAEsRAP/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFB" +
            "BhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqS" +
            "k5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/aAA4EQwBNAFkASwAAPwD9U6/Kqvyq" +
            "r9U6/9k="
}

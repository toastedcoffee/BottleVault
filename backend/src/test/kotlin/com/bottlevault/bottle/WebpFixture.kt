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

    /**
     * The start of an extended (VP8X) WebP declaring a [width] x [height] canvas,
     * with no image data: enough for a reader to report the size, nothing more.
     */
    fun vp8xHeader(width: Int, height: Int): ByteArray {
        fun le24(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte())
        val vp8x = byteArrayOf(0, 0, 0, 0) + le24(width - 1) + le24(height - 1)
        val chunk = "VP8X".toByteArray() + byteArrayOf(10, 0, 0, 0) + vp8x
        val body = "WEBP".toByteArray() + chunk
        val size = body.size
        return "RIFF".toByteArray() + byteArrayOf(size.toByte(), (size shr 8).toByte(), 0, 0) + body
    }

    private const val ENCODED = "UklGRiwAAABXRUJQVlA4TB8AAAAvAUAAAB8gEEjeHzqN+RcQFPwf3fxHZA/gBgwR/Q8BAA=="

    private const val CMYK =
        "/9j/7gAOQWRvYmUAZAAAAAAA/9sAQwADAgIDAgIDAwMDBAMDBAUIBQUEBAUKBwcGCAwKDAwLCgsLDQ4SEA0OEQ4LCxAWEBETFBUVFQwPFxgWFBgSFBUU" +
            "/8AAFAgACAAIBEMRAE0RAFkRAEsRAP/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFB" +
            "BhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqS" +
            "k5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/aAA4EQwBNAFkASwAAPwD9U6/Kqvyq" +
            "r9U6/9k="
}

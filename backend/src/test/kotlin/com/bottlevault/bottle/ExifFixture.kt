// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Hand-built EXIF (APP1) segments for tests: IFD0 with Orientation, optionally a GPS IFD. */
object ExifFixture {
    fun app1(orientation: Int, withGps: Boolean): ByteArray {
        val tiff = ByteArrayOutputStream()
        DataOutputStream(tiff).apply {
            writeBytes("MM"); writeShort(42); writeInt(8)          // big-endian TIFF, IFD0 at offset 8
            val entries = if (withGps) 2 else 1
            writeShort(entries)
            writeShort(0x0112); writeShort(3); writeInt(1); writeShort(orientation); writeShort(0)   // Orientation
            if (withGps) {
                val gpsOffset = 8 + 2 + entries * 12 + 4
                writeShort(0x8825); writeShort(4); writeInt(1); writeInt(gpsOffset)                // GPS IFD pointer
            }
            writeInt(0)                                            // no next IFD
            if (withGps) {
                writeShort(1)                                      // one GPS entry
                writeShort(0x0001); writeShort(2); writeInt(2); writeBytes("N"); writeByte(0); writeShort(0) // GPSLatitudeRef "N"
                writeInt(0)
            }
        }
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff.toByteArray()
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }
}

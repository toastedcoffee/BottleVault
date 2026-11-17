// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

object PngFixture {
    fun headerOnly(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val ihdr = ByteArrayOutputStream().also { b ->
                DataOutputStream(b).apply { writeInt(width); writeInt(height); writeByte(8); writeByte(2); writeByte(0); writeByte(0); writeByte(0) }
            }.toByteArray()
            chunk(this, "IHDR", ihdr)
            chunk(this, "IEND", ByteArray(0))
        }
        return out.toByteArray()
    }

    private fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
        out.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        out.writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
    }
}

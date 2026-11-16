// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import java.security.MessageDigest
import java.util.Locale

/** The invite code format shared with scripts/invite.sh. */
object InviteCodes {
    /** Crockford base32: no I, L, O or U, so a code survives being read aloud. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 16

    /**
     * Upper-cases, drops dashes and spaces, and maps the Crockford look-alikes
     * (O→0, I/L→1). Returns null when the result cannot be a code — before any
     * database lookup.
     */
    fun normalize(input: String?): String? {
        val s = input.orEmpty().uppercase(Locale.ROOT)
            .filterNot { it == '-' || it.isWhitespace() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")
        return s.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    fun hash(normalized: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
}

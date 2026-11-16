// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

object PasswordRules {
    /** bcrypt silently ignores everything past 72 bytes; refuse rather than truncate. */
    const val MAX_BYTES = 72

    fun check(password: String) {
        require(password.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Password must be at most $MAX_BYTES bytes (shorter if it uses accented letters or emoji)"
        }
    }
}

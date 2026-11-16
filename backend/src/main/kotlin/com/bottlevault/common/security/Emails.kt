// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import java.util.Locale

object Emails {
    /** The one spelling of an email address the app stores, compares and hashes. */
    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)
}

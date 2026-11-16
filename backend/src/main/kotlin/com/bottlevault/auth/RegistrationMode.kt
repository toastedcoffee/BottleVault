// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

enum class RegistrationMode {
    OPEN, INVITE, CLOSED;

    companion object {
        /**
         * REGISTRATION_MODE wins when set; otherwise the older REGISTRATION_ENABLED
         * switch decides, so existing deployments keep behaving as before. An
         * unknown value stops startup rather than guessing — guessing wrong could
         * open registration to everyone.
         */
        fun resolve(mode: String, legacyEnabled: Boolean): RegistrationMode {
            if (mode.isBlank()) return if (legacyEnabled) OPEN else CLOSED
            return entries.firstOrNull { it.name.equals(mode.trim(), ignoreCase = true) }
                ?: throw IllegalStateException("REGISTRATION_MODE must be open, invite or closed (got '$mode')")
        }
    }
}

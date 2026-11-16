// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Keyed hashes of identifiers for the security log: the log can show that many
 * failures came from one place without storing where that place is. The key is
 * what keeps the hashes irreversible — the whole IPv4 space is small enough to
 * brute-force an unkeyed hash.
 */
@Component
class IdentifierHasher(@Value("\${app.security.ip-hash-key:}") configuredKey: String) {

    private val key: ByteArray =
        if (configuredKey.isNotBlank()) {
            configuredKey.toByteArray(Charsets.UTF_8)
        } else {
            LoggerFactory.getLogger(javaClass).warn(
                "IP_HASH_KEY is not set: using a random key for this run, so hashed " +
                    "addresses in the security log will not match across restarts."
            )
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        }

    // Mac is not thread-safe; one initialised instance per thread avoids a provider
    // lookup and key setup on every request.
    private val macs: ThreadLocal<Mac> = ThreadLocal.withInitial {
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
    }

    /** First 16 hex characters of HMAC-SHA256(key, canonical). */
    fun hash(canonical: String): String {
        return macs.get().doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(0, 16)
    }

    fun hashEmail(email: String): String = hash(Emails.normalize(email))
}

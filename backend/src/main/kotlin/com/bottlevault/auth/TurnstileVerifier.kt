// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.auth

import com.bottlevault.common.exception.ApiException
import com.bottlevault.common.security.ClientIp
import com.bottlevault.common.security.OutboundHttp
import com.bottlevault.common.security.SecurityEvents
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClientException

@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteverifyResponse(
    val success: Boolean = false,
    val hostname: String? = null,
    val action: String? = null,
    @JsonProperty("error-codes") val errorCodes: List<String>? = null,
    val metadata: Map<String, Any?>? = null,
) {
    /**
     * Cloudflare's published test secrets answer every token with success, no
     * action and the hostname example.com, and mark the reply as a test result.
     * Only a test secret produces this, and only Cloudflare can send it.
     */
    val fromTestingKey: Boolean get() = metadata?.get("result_with_testing_key") == true
}

/**
 * Cloudflare Turnstile, checked on the server: the browser widget alone proves
 * nothing, because a script can call the API without it. Tokens are single-use
 * and expire after 300 s. If Cloudflare cannot be reached, sign-in fails closed —
 * an outage there also takes down the tunnel, so failing open would buy nothing.
 *
 * Off unless TURNSTILE_SECRET_KEY is set, so self-hosters without Cloudflare
 * see no change.
 */
@Component
class TurnstileVerifier(
    private val outboundHttp: OutboundHttp,
    private val securityEvents: SecurityEvents,
    @Value("\${app.turnstile.secret-key:}") private val secretKey: String,
    @Value("\${app.turnstile.expected-hostname:}") private val expectedHostname: String,
    @Value("\${app.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") private val verifyUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requireHuman(token: String?, action: String, client: ClientIp) {
        if (secretKey.isBlank()) return
        if (token.isNullOrBlank()) refuse(action, "missing", client)
        val form = LinkedMultiValueMap<String, String>().apply {
            add("secret", secretKey)
            add("response", token)
            add("remoteip", client.address)
        }
        val result = try {
            outboundHttp.restClient().post()
                .uri(verifyUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(SiteverifyResponse::class.java)
        } catch (e: RestClientException) {
            securityEvents.emit("turnstile.unavailable", clientIp = client)
            throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Verification is temporarily unavailable. Please try again in a minute.",
            )
        }
        if (result != null && result.success && result.fromTestingKey) {
            // A test secret is configured: no real check happened. Fine for local
            // testing; the warning makes it visible if it ever reaches a real stack.
            log.warn("Turnstile is configured with a Cloudflare test key: every visitor passes the check")
            return
        }
        val accepted = result != null && result.success && result.action == action &&
            (expectedHostname.isBlank() || result.hostname == expectedHostname)
        if (!accepted) {
            val detail = result?.errorCodes?.takeIf { it.isNotEmpty() }?.joinToString(",")
                ?: if (result?.success == true) "action_or_hostname_mismatch" else "rejected"
            refuse(action, detail, client)
        }
    }

    private fun refuse(action: String, detail: String, client: ClientIp): Nothing {
        securityEvents.emit("auth.$action.failure", attrs = mapOf("reason" to "turnstile", "detail" to detail), clientIp = client)
        throw ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Please complete the verification and try again.")
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

/**
 * The security log: one JSON object per line on the SECURITY logger, and in
 * LOG_DIR/security.log when LOG_DIR is set.
 *
 * Invariant: no raw IP address, raw email, password, token or invite code ever
 * reaches this log. Addresses and emails appear only as keyed hashes
 * (IdentifierHasher), which is what lets the maintainer see patterns without
 * keeping who-connected-from-where.
 */
@Component
class SecurityEvents(
    private val clientIpResolver: ClientIpResolver,
    private val hasher: IdentifierHasher,
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    @Value("\${app.security.log-dir:}") logDir: String,
) {
    private val log = LoggerFactory.getLogger(LOGGER_NAME)

    init {
        if (logDir.isNotBlank()) {
            SecurityLogFile.attach(logDir)
            ApplicationLogFile.attach(logDir)
        }
    }

    fun emit(
        type: String,
        userId: UUID? = null,
        email: String? = null,
        attrs: Map<String, Any?> = emptyMap(),
        clientIp: ClientIp? = null,
    ) {
        val event = linkedMapOf<String, Any?>("ts" to clock.instant().toString(), "type" to type)
        (clientIp ?: currentRequest()?.let(clientIpResolver::resolve))?.let { event["ipHash"] = it.hash }
        email?.let { event["emailHash"] = hasher.hashEmail(it) }
        userId?.let { event["userId"] = it.toString() }
        attrs.filterValues { it != null }.takeIf { it.isNotEmpty() }?.let { event["attrs"] = it }
        log.info(objectMapper.writeValueAsString(event))
    }

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    companion object {
        const val LOGGER_NAME = "SECURITY"
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

/**
 * The one way this app talks to other hosts. In the hardened deployment the API
 * has no direct route off the machine: EGRESS_PROXY points at the egress proxy,
 * which allows a short list of HTTPS hosts. The timeouts stop a slow provider
 * from holding request threads hostage.
 *
 * Keep JdkClientHttpRequestFactory. Its read timeout is a deadline for the whole
 * response, body included; SimpleClientHttpRequestFactory's is per socket read, so a
 * server trickling one byte every few seconds never trips it. (Measured on Spring
 * 7.0.9 against local stall and trickle servers, 2026-09-28.)
 */
@Component
class OutboundHttp(
    @Value("\${app.egress.proxy:}") proxyUrl: String,
    @Value("\${app.egress.connect-timeout:5s}") connectTimeout: Duration,
    @Value("\${app.egress.read-timeout:10s}") private val readTimeout: Duration,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .proxy(proxySelector(proxyUrl))
        .build()

    fun restClient(): RestClient =
        RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(readTimeout) })
            .build()

    companion object {
        internal fun proxySelector(proxyUrl: String): ProxySelector {
            if (proxyUrl.isBlank()) return HttpClient.Builder.NO_PROXY
            val uri = runCatching { URI.create(proxyUrl.trim()) }.getOrNull()
            require(uri != null && uri.scheme == "http" && uri.host != null && uri.port > 0) {
                "EGRESS_PROXY must look like http://host:port (got '$proxyUrl')"
            }
            // Unresolved: the proxy's name is looked up when connecting, so the API
            // can start before the proxy container does.
            return ProxySelector.of(InetSocketAddress.createUnresolved(uri.host, uri.port))
        }
    }
}

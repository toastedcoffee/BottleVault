// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.barcode

import com.bottlevault.barcode.dto.BarcodeLookupResponse
import com.bottlevault.common.exception.RateLimitedException
import com.bottlevault.common.security.RateLimiter
import com.bottlevault.common.security.SecurityEvents
import com.bottlevault.product.ProductRepository
import com.bottlevault.product.dto.ProductResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class BarcodeService(
    private val productRepository: ProductRepository,
    private val missRepository: BarcodeLookupMissRepository,
    providers: List<BarcodeProvider>,
    private val rateLimiter: RateLimiter,
    private val securityEvents: SecurityEvents,
    transactionManager: PlatformTransactionManager,
    @Value("\${app.barcode.miss-ttl-days:7}") missTtlDays: Long,
    @Value("\${app.rate-limit.user.barcode-lookups-per-day:30}") private val lookupsPerDay: Int,
    @Value("\${app.rate-limit.provider.upcitemdb-per-day:90}") upcItemDbPerDay: Int,
    @Value("\${app.rate-limit.provider.openfoodfacts-per-day:1000}") openFoodFactsPerDay: Int,
    @Value("\${app.rate-limit.provider.barcodelookup-per-day:1000}") barcodeLookupPerDay: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val providers: List<BarcodeProvider> = providers
    private val missTtl: Duration = Duration.ofDays(missTtlDays)

    // Each provider's free tier is per IP, so its cap is shared by every user.
    private val providerCaps = mapOf(
        "upcitemdb" to upcItemDbPerDay,
        "openfoodfacts" to openFoodFactsPerDay,
        "barcodelookup" to barcodeLookupPerDay,
    )

    // Short transactions only. A full miss can wait on three providers, up to
    // about 10 s each, and a transaction held across them would pin a pooled
    // connection per in-flight lookup: a burst of unknown barcodes would then
    // starve every other request.
    private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writes = TransactionTemplate(transactionManager)

    init {
        log.info(
            "BarcodeService configured with providers: {} (miss-cache TTL: {})",
            this.providers.sortedBy { it.priority }.joinToString { "${it.source}(p=${it.priority},enabled=${it.isEnabled})" },
            missTtl
        )
    }

    private sealed interface LocalAnswer
    private data class Hit(val response: BarcodeLookupResponse) : LocalAnswer
    private data class Miss(val cachedAt: Instant?) : LocalAnswer

    fun lookupBarcode(barcode: String, userId: UUID): BarcodeLookupResponse {
        val local = readOnly.execute {
            productRepository.findByBarcode(barcode)?.let {
                // Mapped inside the transaction: Product.brand is lazy.
                Hit(BarcodeLookupResponse(found = true, source = "local", product = ProductResponse.from(it)))
            } ?: Miss(missRepository.findById(barcode).orElse(null)?.lastAttemptAt)
        }!!
        if (local is Hit) return local.response
        val cachedAt = (local as Miss).cachedAt
        if (cachedAt != null && cachedAt.isAfter(Instant.now().minus(missTtl))) {
            return BarcodeLookupResponse(found = false, source = "cached-miss")
        }

        // Only lookups that leave the building count: they spend a shared,
        // IP-bound provider quota. Local and cached answers above are free.
        val budget = rateLimiter.tryAcquire("barcode-external", userId.toString(), lookupsPerDay, Duration.ofDays(1))
        if (!budget.allowed) {
            securityEvents.emit("ratelimit.tripped", userId = userId, attrs = mapOf("bucket" to "barcode-external"))
            throw RateLimitedException(
                "You've reached today's limit of $lookupsPerDay barcode lookups. Enter the details by hand, or try again tomorrow.",
                budget.retryAfterSeconds,
            )
        }

        var skipped = false
        for (provider in providers.sortedBy { it.priority }) {
            if (!provider.isEnabled) continue
            val cap = providerCaps[provider.source] ?: DEFAULT_PROVIDER_CAP
            if (!rateLimiter.tryAcquire("provider:${provider.source}", "all", cap, Duration.ofDays(1)).allowed) {
                securityEvents.emit("barcode.provider.capped", attrs = mapOf("provider" to provider.source))
                skipped = true
                continue
            }
            val data = provider.lookup(barcode) ?: continue
            if (cachedAt != null) writes.executeWithoutResult { missRepository.deleteById(barcode) }
            return BarcodeLookupResponse(found = true, source = provider.source, externalProduct = data)
        }

        // A provider we skipped might have known it: don't remember this as a miss.
        if (!skipped) recordMiss(barcode)
        return BarcodeLookupResponse(found = false, source = null)
    }

    private fun recordMiss(barcode: String) {
        try {
            writes.executeWithoutResult {
                val now = Instant.now()
                val row = missRepository.findById(barcode).orElse(null)?.apply {
                    lastAttemptAt = now
                    attempts += 1
                } ?: BarcodeLookupMiss(barcode = barcode, lastAttemptAt = now, attempts = 1)
                missRepository.saveAndFlush(row)
            }
        } catch (e: DataIntegrityViolationException) {
            // Two lookups of the same unknown barcode both tried to insert its
            // miss row. The other one's row stands, and "not found" is still the
            // right answer for this request.
            log.debug("Miss row for {} was inserted concurrently", barcode)
        }
    }

    private companion object {
        const val DEFAULT_PROVIDER_CAP = 1000
    }
}

package com.bottlevault.barcode

import com.bottlevault.barcode.dto.BarcodeLookupResponse
import com.bottlevault.product.ProductRepository
import com.bottlevault.product.dto.ProductResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class BarcodeService(
    private val productRepository: ProductRepository,
    private val missRepository: BarcodeLookupMissRepository,
    providers: List<BarcodeProvider>,
    @Value("\${app.barcode.miss-ttl-days:7}") missTtlDays: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val providers: List<BarcodeProvider> = providers
    private val missTtl: Duration = Duration.ofDays(missTtlDays)

    init {
        log.info(
            "BarcodeService configured with providers: {} (miss-cache TTL: {})",
            providers.sortedBy { it.priority }
                .joinToString { "${it.source}(p=${it.priority},enabled=${it.isEnabled})" },
            missTtl
        )
    }

    @Transactional
    fun lookupBarcode(barcode: String): BarcodeLookupResponse {
        productRepository.findByBarcode(barcode)?.let {
            return BarcodeLookupResponse(
                found = true,
                source = "local",
                product = ProductResponse.from(it)
            )
        }

        val cachedMiss = missRepository.findById(barcode).orElse(null)
        if (cachedMiss != null && cachedMiss.lastAttemptAt.isAfter(Instant.now().minus(missTtl))) {
            return BarcodeLookupResponse(found = false, source = "cached-miss")
        }

        for (provider in providers.sortedBy { it.priority }) {
            if (!provider.isEnabled) continue
            val data = provider.lookup(barcode) ?: continue

            if (cachedMiss != null) missRepository.deleteById(barcode)

            return BarcodeLookupResponse(
                found = true,
                source = provider.source,
                externalProduct = data
            )
        }

        recordMiss(cachedMiss, barcode)
        return BarcodeLookupResponse(found = false, source = null)
    }

    private fun recordMiss(existing: BarcodeLookupMiss?, barcode: String) {
        val now = Instant.now()
        val row = existing?.apply {
            lastAttemptAt = now
            attempts += 1
        } ?: BarcodeLookupMiss(barcode = barcode, lastAttemptAt = now, attempts = 1)
        missRepository.save(row)
    }
}

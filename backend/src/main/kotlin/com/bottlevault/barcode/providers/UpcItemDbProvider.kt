package com.bottlevault.barcode.providers

import com.bottlevault.barcode.AbvParser
import com.bottlevault.barcode.BarcodeProvider
import com.bottlevault.barcode.dto.ExternalProductData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class UpcItemDbProvider : BarcodeProvider {
    override val source = "upcitemdb"
    override val priority = 10
    override val isEnabled = true

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    override fun lookup(barcode: String): ExternalProductData? {
        return try {
            val response = restClient.get()
                .uri("https://api.upcitemdb.com/prod/trial/lookup?upc={upc}", barcode)
                .retrieve()
                .body(UpcItemDbResponse::class.java)

            val item = response?.items?.firstOrNull() ?: return null
            val name = item.title?.takeIf { it.isNotBlank() } ?: return null

            ExternalProductData(
                name = name,
                brandName = item.brand?.takeIf { it.isNotBlank() },
                barcode = barcode,
                size = item.size?.takeIf { it.isNotBlank() },
                abv = AbvParser.parse(item.title) ?: AbvParser.parse(item.description),
                imageUrl = item.images?.firstOrNull()?.takeIf { it.isNotBlank() },
                categories = item.category?.takeIf { it.isNotBlank() }
            )
        } catch (e: RestClientException) {
            log.warn("UPCitemdb lookup failed for {}: {}", barcode, e.message)
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class UpcItemDbResponse(
    val code: String?,
    val total: Int?,
    val items: List<UpcItemDbItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class UpcItemDbItem(
    val ean: String?,
    val title: String?,
    val brand: String?,
    val category: String?,
    val description: String?,
    val size: String?,
    val images: List<String>?
)

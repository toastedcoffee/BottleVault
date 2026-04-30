package com.bottlevault.barcode.providers

import com.bottlevault.barcode.AbvParser
import com.bottlevault.barcode.BarcodeProvider
import com.bottlevault.barcode.dto.ExternalProductData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class BarcodeLookupProvider(
    @Value("\${app.barcode.barcodelookup.api-key:}") private val apiKey: String
) : BarcodeProvider {
    override val source = "barcodelookup"
    override val priority = 20
    override val isEnabled: Boolean
        get() = apiKey.isNotBlank()

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    override fun lookup(barcode: String): ExternalProductData? {
        if (!isEnabled) return null
        return try {
            val response = restClient.get()
                .uri(
                    "https://api.barcodelookup.com/v3/products?barcode={barcode}&formatted=y&key={key}",
                    barcode,
                    apiKey
                )
                .retrieve()
                .body(BarcodeLookupApiResponse::class.java)

            val product = response?.products?.firstOrNull() ?: return null
            val name = (product.title ?: product.productName)?.takeIf { it.isNotBlank() } ?: return null

            ExternalProductData(
                name = name,
                brandName = (product.brand ?: product.manufacturer)?.takeIf { it.isNotBlank() },
                barcode = barcode,
                size = product.size?.takeIf { it.isNotBlank() },
                abv = AbvParser.parse(name) ?: AbvParser.parse(product.description),
                imageUrl = product.images?.firstOrNull()?.takeIf { it.isNotBlank() },
                categories = product.category?.takeIf { it.isNotBlank() }
            )
        } catch (e: RestClientException) {
            log.warn("BarcodeLookup lookup failed for {}: {}", barcode, e.message)
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class BarcodeLookupApiResponse(
    val products: List<BarcodeLookupProduct>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class BarcodeLookupProduct(
    val barcode: String?,
    val title: String?,
    @JsonProperty("product_name") val productName: String?,
    val brand: String?,
    val manufacturer: String?,
    val category: String?,
    val description: String?,
    val size: String?,
    val images: List<String>?
)

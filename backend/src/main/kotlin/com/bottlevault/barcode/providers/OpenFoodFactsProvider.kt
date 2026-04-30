package com.bottlevault.barcode.providers

import com.bottlevault.barcode.AbvParser
import com.bottlevault.barcode.BarcodeProvider
import com.bottlevault.barcode.dto.ExternalProductData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class OpenFoodFactsProvider : BarcodeProvider {
    override val source = "openfoodfacts"
    override val priority = 30
    override val isEnabled = true

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    override fun lookup(barcode: String): ExternalProductData? {
        return try {
            val response = restClient.get()
                .uri("https://world.openfoodfacts.org/api/v2/product/{barcode}.json", barcode)
                .retrieve()
                .body(OpenFoodFactsResponse::class.java)

            val product = response?.takeIf { it.status == 1 }?.product ?: return null

            ExternalProductData(
                name = product.productName ?: product.genericName ?: return null,
                brandName = product.brands?.takeIf { it.isNotBlank() },
                barcode = barcode,
                size = product.quantity?.takeIf { it.isNotBlank() },
                abv = AbvParser.parse(product.alcoholByVolume),
                imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                categories = product.categories?.takeIf { it.isNotBlank() }
            )
        } catch (e: RestClientException) {
            log.warn("OpenFoodFacts lookup failed for {}: {}", barcode, e.message)
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenFoodFactsResponse(
    val status: Int?,
    val product: OpenFoodFactsProduct?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenFoodFactsProduct(
    @JsonProperty("product_name") val productName: String?,
    @JsonProperty("generic_name") val genericName: String?,
    val brands: String?,
    val quantity: String?,
    @JsonProperty("alcohol_by_volume") val alcoholByVolume: String?,
    @JsonProperty("image_url") val imageUrl: String?,
    @JsonProperty("categories") val categories: String?
)

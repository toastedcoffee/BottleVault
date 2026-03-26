package com.bottlevault.barcode

import com.bottlevault.product.ProductRepository
import com.bottlevault.product.dto.ProductResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class BarcodeService(
    private val productRepository: ProductRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    fun lookupBarcode(barcode: String): BarcodeLookupResponse {
        // First check local database
        val localProduct = productRepository.findByBarcode(barcode)
        if (localProduct != null) {
            return BarcodeLookupResponse(
                found = true,
                source = "local",
                product = ProductResponse.from(localProduct)
            )
        }

        // Try Open Food Facts API
        val externalResult = lookupOpenFoodFacts(barcode)
        if (externalResult != null) {
            return BarcodeLookupResponse(
                found = true,
                source = "openfoodfacts",
                externalProduct = externalResult
            )
        }

        return BarcodeLookupResponse(found = false, source = null)
    }

    private fun lookupOpenFoodFacts(barcode: String): ExternalProductData? {
        return try {
            val response = restClient.get()
                .uri("https://world.openfoodfacts.org/api/v2/product/{barcode}.json", barcode)
                .retrieve()
                .body(OpenFoodFactsResponse::class.java)

            if (response?.status == 1 && response.product != null) {
                val p = response.product
                ExternalProductData(
                    name = p.productName ?: p.genericName ?: "Unknown Product",
                    brandName = p.brands,
                    barcode = barcode,
                    size = p.quantity,
                    abv = parseAbv(p.alcoholByVolume),
                    imageUrl = p.imageUrl,
                    categories = p.categories
                )
            } else null
        } catch (e: RestClientException) {
            log.warn("Open Food Facts lookup failed for barcode {}: {}", barcode, e.message)
            null
        }
    }

    private fun parseAbv(abvString: String?): Double? {
        if (abvString.isNullOrBlank()) return null
        return abvString.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
    }
}

// Response DTOs
data class BarcodeLookupResponse(
    val found: Boolean,
    val source: String?,
    val product: ProductResponse? = null,
    val externalProduct: ExternalProductData? = null
)

data class ExternalProductData(
    val name: String,
    val brandName: String?,
    val barcode: String,
    val size: String?,
    val abv: Double?,
    val imageUrl: String?,
    val categories: String?
)

// Open Food Facts API response mapping
data class OpenFoodFactsResponse(
    val status: Int?,
    val product: OpenFoodFactsProduct?
)

data class OpenFoodFactsProduct(
    val productName: String?,
    val genericName: String?,
    val brands: String?,
    val quantity: String?,
    val alcoholByVolume: String?,
    val imageUrl: String?,
    val categories: String?
)

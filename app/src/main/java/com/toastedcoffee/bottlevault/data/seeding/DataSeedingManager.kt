package com.toastedcoffee.bottlevault.data.seeding

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.toastedcoffee.bottlevault.data.local.database.*
import com.toastedcoffee.bottlevault.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSeedingManager @Inject constructor(
    private val context: Context,
    private val brandDao: BrandDao,
    private val productDao: ProductDao,
    private val gson: Gson
) {

    suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        try {
            // Load and insert brands
            val brands = loadBrandsFromAssets()
            brands.forEach { brand ->
                brandDao.insertBrand(brand)
            }

            // Load and insert products
            val products = loadProductsFromAssets()
            products.forEach { product ->
                productDao.insertProduct(product)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadBrandsFromAssets(): List<Brand> {
        return try {
            val json = context.assets.open("brands.json").bufferedReader().use { it.readText() }
            val brandDataList: List<BrandData> = gson.fromJson(json, object : TypeToken<List<BrandData>>() {}.type)

            brandDataList.map { brandData ->
                Brand(
                    id = brandData.id,
                    name = brandData.name,
                    country = brandData.country,
                    website = brandData.website,
                    syncStatus = SyncStatus.SYNCED
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun loadProductsFromAssets(): List<Product> {
        return try {
            val json = context.assets.open("products.json").bufferedReader().use { it.readText() }
            val productDataList: List<ProductData> = gson.fromJson(json, object : TypeToken<List<ProductData>>() {}.type)

            productDataList.map { productData ->
                Product(
                    id = productData.id,
                    brandId = productData.brandId,
                    name = productData.name,
                    barcode = productData.barcode,
                    type = AlcoholType.valueOf(productData.type),
                    subtype = productData.subtype,
                    size = productData.size,
                    abv = productData.abv,
                    description = productData.description,
                    imageUrl = productData.imageUrl,
                    isUserCreated = false,
                    syncStatus = SyncStatus.SYNCED
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

data class BrandData(
    val id: String,
    val name: String,
    val country: String?,
    val website: String?
)

data class ProductData(
    val id: String,
    val brandId: String,
    val name: String,
    val barcode: String?,
    val type: String,
    val subtype: String?,
    val size: String?,
    val abv: Double?,
    val description: String?,
    val imageUrl: String?
)
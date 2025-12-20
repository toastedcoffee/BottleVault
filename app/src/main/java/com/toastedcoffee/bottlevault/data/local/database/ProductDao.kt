package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import com.toastedcoffee.bottlevault.data.model.AlcoholType
import com.toastedcoffee.bottlevault.data.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE brandId = :brandId ORDER BY name ASC")
    fun getProductsByBrand(brandId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE type = :type ORDER BY name ASC")
    fun getProductsByType(type: AlcoholType): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): Product?

    @Query("""
        SELECT p.* FROM products p
        INNER JOIN brands b ON p.brandId = b.id
        WHERE p.name LIKE '%' || :query || '%' OR b.name LIKE '%' || :query || '%'
        ORDER BY p.name ASC
    """)
    suspend fun searchProducts(query: String): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Query("SELECT * FROM products WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncProducts(): List<Product>
}
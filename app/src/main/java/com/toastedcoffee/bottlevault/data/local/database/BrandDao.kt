package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import com.toastedcoffee.bottlevault.data.model.Brand
import com.toastedcoffee.bottlevault.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY name ASC")
    fun getAllBrands(): Flow<List<Brand>>

    @Query("SELECT * FROM brands WHERE id = :brandId LIMIT 1")
    suspend fun getBrandById(brandId: String): Brand?

    @Query("SELECT * FROM brands WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchBrands(query: String): List<Brand>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrand(brand: Brand)

    @Query("SELECT * FROM brands WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncBrands(): List<Brand>
}
package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import com.toastedcoffee.bottlevault.data.model.AlcoholType
import com.toastedcoffee.bottlevault.data.model.BottleWithProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface BottleWithProductDao {
    @Query("SELECT * FROM BottleWithProduct WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY lastModified DESC")
    fun getAllBottlesWithProductForUser(userId: String): Flow<List<BottleWithProduct>>

    @Query("""
        SELECT * FROM BottleWithProduct 
        WHERE userId = :userId 
        AND (productName LIKE '%' || :query || '%' OR brandName LIKE '%' || :query || '%')
        AND syncStatus != 'PENDING_DELETE'
        ORDER BY productName ASC
    """)
    fun searchBottlesWithProduct(userId: String, query: String): Flow<List<BottleWithProduct>>

    @Query("SELECT * FROM BottleWithProduct WHERE userId = :userId AND type = :type AND syncStatus != 'PENDING_DELETE'")
    fun getBottlesWithProductByType(userId: String, type: AlcoholType): Flow<List<BottleWithProduct>>
}
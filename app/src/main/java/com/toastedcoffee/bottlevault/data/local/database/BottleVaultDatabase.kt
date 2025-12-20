package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.toastedcoffee.bottlevault.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [Brand::class, Product::class, Bottle::class, User::class],
    views = [BottleWithProduct::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BottleVaultDatabase : RoomDatabase() {

    abstract fun brandDao(): BrandDao
    abstract fun productDao(): ProductDao
    abstract fun bottleDao(): BottleDao
    abstract fun userDao(): UserDao
    abstract fun bottleWithProductDao(): BottleWithProductDao

    companion object {
        @Volatile
        private var INSTANCE: BottleVaultDatabase? = null

        fun getDatabase(
            context: android.content.Context,
            scope: CoroutineScope
        ): BottleVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BottleVaultDatabase::class.java,
                    "bottlevault_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Database callback to populate database with initial data
     */
    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    // Data seeding will be handled by DataSeedingManager
                }
            }
        }
    }
}
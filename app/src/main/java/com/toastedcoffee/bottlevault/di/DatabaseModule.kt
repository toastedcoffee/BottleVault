package com.toastedcoffee.bottlevault.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.toastedcoffee.bottlevault.data.local.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @ApplicationScope
    @Provides
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): BottleVaultDatabase {
        return BottleVaultDatabase.getDatabase(context, scope)
    }

    @Provides
    fun provideBrandDao(database: BottleVaultDatabase) = database.brandDao()

    @Provides
    fun provideProductDao(database: BottleVaultDatabase) = database.productDao()

    @Provides
    fun provideBottleDao(database: BottleVaultDatabase) = database.bottleDao()

    @Provides
    fun provideUserDao(database: BottleVaultDatabase) = database.userDao()

    @Provides
    fun provideBottleWithProductDao(database: BottleVaultDatabase) = database.bottleWithProductDao()
    // ADD THESE TWO METHODS:
    @Singleton
    @Provides
    fun provideGson(): Gson {
        return Gson()
    }

    @Singleton
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
}
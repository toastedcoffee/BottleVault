package com.toastedcoffee.bottlevault

import android.app.Application
import com.toastedcoffee.bottlevault.data.seeding.DataSeedingManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BottleVaultApplication : Application() {

    @Inject
    lateinit var dataSeedingManager: DataSeedingManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize data seeding on app startup
        applicationScope.launch {
            try {
                dataSeedingManager.seedInitialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
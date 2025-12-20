// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/MainActivity.kt
package com.toastedcoffee.bottlevault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.toastedcoffee.bottlevault.ui.navigation.BottleVaultNavHost
import com.toastedcoffee.bottlevault.ui.theme.BottleVaultTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BottleVaultTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BottleVaultNavHost(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
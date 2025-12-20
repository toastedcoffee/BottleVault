// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/navigation/BottleVaultNavHost.kt
package com.toastedcoffee.bottlevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toastedcoffee.bottlevault.ui.addedit.AddEditBottleScreen
import com.toastedcoffee.bottlevault.ui.addedit.AddEditBottleViewModel
import com.toastedcoffee.bottlevault.ui.inventory.InventoryScreen
import com.toastedcoffee.bottlevault.ui.inventory.InventoryViewModel

// Navigation routes
object Routes {
    const val INVENTORY = "inventory"
    const val ADD_BOTTLE = "add_bottle"
    const val EDIT_BOTTLE = "edit_bottle/{bottleId}"

    fun editBottle(bottleId: String) = "edit_bottle/$bottleId"
}

@Composable
fun BottleVaultNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.INVENTORY
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.INVENTORY) {
            val viewModel: InventoryViewModel = hiltViewModel()
            InventoryScreen(
                viewModel = viewModel,
                onAddBottleClick = {
                    navController.navigate(Routes.ADD_BOTTLE)
                },
                onBottleClick = { bottleId ->
                    navController.navigate(Routes.editBottle(bottleId))
                }
            )
        }

        composable(Routes.ADD_BOTTLE) {
            val viewModel: AddEditBottleViewModel = hiltViewModel()
            AddEditBottleScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                isEditMode = false
            )
        }

        composable(Routes.EDIT_BOTTLE) { backStackEntry ->
            val bottleId = backStackEntry.arguments?.getString("bottleId") ?: return@composable
            val viewModel: AddEditBottleViewModel = hiltViewModel()
            AddEditBottleScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                isEditMode = true,
                bottleId = bottleId
            )
        }
    }
}
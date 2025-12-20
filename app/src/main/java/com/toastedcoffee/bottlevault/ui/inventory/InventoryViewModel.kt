// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/inventory/InventoryViewModel.kt
package com.toastedcoffee.bottlevault.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toastedcoffee.bottlevault.data.model.Bottle
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.BottleWithProduct
import com.toastedcoffee.bottlevault.data.repository.BottleRepository
import com.toastedcoffee.bottlevault.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val bottles: List<BottleWithProduct> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilter: BottleStatus? = null
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val bottleRepository: BottleRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState(isLoading = true))
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow<BottleStatus?>(null)

    init {
        loadBottles()
    }

    private fun loadBottles() {
        viewModelScope.launch {
            try {
                combine(
                    bottleRepository.getAllBottlesWithProducts(),
                    _selectedFilter
                ) { bottles, filter ->
                    val filteredBottles = if (filter != null) {
                        bottles.filter { it.bottle.status == filter }
                    } else {
                        bottles
                    }

                    _uiState.value = _uiState.value.copy(
                        bottles = filteredBottles,
                        isLoading = false,
                        errorMessage = null,
                        selectedFilter = filter
                    )
                }.collect { }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun setStatusFilter(status: BottleStatus?) {
        _selectedFilter.value = status
    }

    fun deleteBottle(bottle: Bottle) {
        viewModelScope.launch {
            try {
                bottleRepository.deleteBottle(bottle.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete bottle"
                )
            }
        }
    }

    fun updateBottleStatus(bottleId: String, newStatus: BottleStatus) {
        viewModelScope.launch {
            try {
                bottleRepository.updateBottleStatus(bottleId, newStatus)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to update bottle status"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refreshData() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadBottles()
    }
}
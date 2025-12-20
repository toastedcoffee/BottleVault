// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/addedit/AddEditBottleViewModel.kt
package com.toastedcoffee.bottlevault.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toastedcoffee.bottlevault.data.model.*
import com.toastedcoffee.bottlevault.data.repository.BottleRepository
import com.toastedcoffee.bottlevault.data.repository.ProductRepository
import com.toastedcoffee.bottlevault.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddEditBottleUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Form data
    val selectedBrand: Brand? = null,
    val selectedProduct: Product? = null,
    val status: BottleStatus = BottleStatus.AVAILABLE,
    val purchaseDate: Date? = null,
    val purchaseLocation: String = "",
    val purchaseCost: String = "",
    val notes: String = "",

    // Available options
    val brands: List<Brand> = emptyList(),
    val productsForBrand: List<Product> = emptyList(),

    // Validation
    val canSave: Boolean = false
)

@HiltViewModel
class AddEditBottleViewModel @Inject constructor(
    private val bottleRepository: BottleRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditBottleUiState(isLoading = true))
    val uiState: StateFlow<AddEditBottleUiState> = _uiState.asStateFlow()

    private var currentBottleId: String? = null
    private var isEditMode: Boolean = false

    init {
        loadInitialData()
    }

    fun initializeForEdit(bottleId: String) {
        if (currentBottleId != bottleId) {
            currentBottleId = bottleId
            isEditMode = true
            loadBottleForEdit(bottleId)
        }
    }

    fun initializeForAdd() {
        currentBottleId = null
        isEditMode = false
        // Reset form to defaults
        _uiState.value = _uiState.value.copy(
            selectedBrand = null,
            selectedProduct = null,
            status = BottleStatus.AVAILABLE,
            purchaseDate = null,
            purchaseLocation = "",
            purchaseCost = "",
            notes = ""
        )
        updateCanSave()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Load all brands (one-time read)
                val brands = productRepository.getAllBrands().first()
                _uiState.value = _uiState.value.copy(
                    brands = brands,
                    isLoading = false
                )
                updateCanSave()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load data"
                )
            }
        }
    }

    private fun loadBottleForEdit(bottleId: String) {
        viewModelScope.launch {
            try {
                val bottleWithProduct = bottleRepository.getBottleWithProduct(bottleId)
                if (bottleWithProduct != null) {
                    // We need to load the actual Brand and Product entities
                    // because UI state expects those types, not the view's data classes
                    val product = productRepository.getProductById(bottleWithProduct.productId)
                    val brand = product?.let { productRepository.getBrandById(it.brandId) }

                    _uiState.value = _uiState.value.copy(
                        selectedBrand = brand,
                        selectedProduct = product,
                        status = bottleWithProduct.status,
                        purchaseDate = bottleWithProduct.purchaseDate,
                        purchaseLocation = bottleWithProduct.purchaseLocation ?: "",
                        purchaseCost = bottleWithProduct.purchaseCost?.toString() ?: "",
                        notes = bottleWithProduct.notes ?: ""
                    )

                    // Load products for this brand
                    brand?.let { loadProductsForBrand(it.id) }
                    updateCanSave()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to load bottle"
                )
            }
        }
    }

    fun selectBrand(brand: Brand) {
        _uiState.value = _uiState.value.copy(
            selectedBrand = brand,
            selectedProduct = null, // Clear product when brand changes
            productsForBrand = emptyList()
        )
        loadProductsForBrand(brand.id)
        updateCanSave()
    }

    private fun loadProductsForBrand(brandId: String) {
        viewModelScope.launch {
            try {
                val products = productRepository.getProductsByBrand(brandId).first()
                _uiState.value = _uiState.value.copy(
                    productsForBrand = products
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to load products"
                )
            }
        }
    }

    fun selectProduct(product: Product) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
        updateCanSave()
    }

    fun updateStatus(status: BottleStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun updatePurchaseDate(date: Date?) {
        _uiState.value = _uiState.value.copy(purchaseDate = date)
    }

    fun updatePurchaseLocation(location: String) {
        _uiState.value = _uiState.value.copy(purchaseLocation = location)
    }

    fun updatePurchaseCost(cost: String) {
        _uiState.value = _uiState.value.copy(purchaseCost = cost)
        updateCanSave()
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    private fun updateCanSave() {
        val state = _uiState.value
        val canSave = state.selectedBrand != null &&
                state.selectedProduct != null &&
                !state.isSaving &&
                (state.purchaseCost.isEmpty() || state.purchaseCost.toDoubleOrNull() != null)

        _uiState.value = state.copy(canSave = canSave)
    }

    fun saveBottle(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "No current user found"
                    )
                    return@launch
                }

                val cost = state.purchaseCost.toDoubleOrNull()

                if (isEditMode && currentBottleId != null) {
                    // Update existing bottle
                    val updatedBottle = Bottle(
                        id = currentBottleId!!,
                        userId = currentUser.id,
                        productId = state.selectedProduct!!.id,
                        status = state.status,
                        purchaseDate = state.purchaseDate,
                        purchaseLocation = state.purchaseLocation.takeIf { it.isNotBlank() },
                        purchaseCost = cost,
                        notes = state.notes.takeIf { it.isNotBlank() }
                    )
                    bottleRepository.updateBottle(updatedBottle)
                } else {
                    // Create new bottle
                    val newBottle = Bottle(
                        id = UUID.randomUUID().toString(),
                        userId = currentUser.id,
                        productId = state.selectedProduct!!.id,
                        status = state.status,
                        purchaseDate = state.purchaseDate,
                        purchaseLocation = state.purchaseLocation.takeIf { it.isNotBlank() },
                        purchaseCost = cost,
                        notes = state.notes.takeIf { it.isNotBlank() }
                    )
                    bottleRepository.insertBottle(newBottle)
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = if (isEditMode) "Bottle updated!" else "Bottle added!"
                )

                onSuccess()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to save bottle"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/addedit/AddEditBottleScreen.kt
package com.toastedcoffee.bottlevault.ui.addedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.Brand
import com.toastedcoffee.bottlevault.data.model.Product
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBottleScreen(
    viewModel: AddEditBottleViewModel,
    onNavigateBack: () -> Unit,
    isEditMode: Boolean,
    bottleId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    // Initialize the view model based on mode
    LaunchedEffect(isEditMode, bottleId) {
        if (isEditMode && bottleId != null) {
            viewModel.initializeForEdit(bottleId)
        } else {
            viewModel.initializeForAdd()
        }
    }

    // Handle success navigation
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditMode) "Edit Bottle" else "Add Bottle")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Brand Selection
                    BrandDropdown(
                        selectedBrand = uiState.selectedBrand,
                        brands = uiState.brands,
                        onBrandSelected = viewModel::selectBrand
                    )

                    // Product Selection
                    ProductDropdown(
                        selectedProduct = uiState.selectedProduct,
                        products = uiState.productsForBrand,
                        enabled = uiState.selectedBrand != null,
                        onProductSelected = viewModel::selectProduct
                    )

                    // Status Selection
                    StatusSelection(
                        selectedStatus = uiState.status,
                        onStatusSelected = viewModel::updateStatus
                    )

                    // Purchase Date
                    PurchaseDateField(
                        selectedDate = uiState.purchaseDate,
                        onDateClick = { showDatePicker = true }
                    )

                    // Purchase Location
                    OutlinedTextField(
                        value = uiState.purchaseLocation,
                        onValueChange = viewModel::updatePurchaseLocation,
                        label = { Text("Purchase Location") },
                        placeholder = { Text("e.g., Total Wine, Costco") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Purchase Cost
                    OutlinedTextField(
                        value = uiState.purchaseCost,
                        onValueChange = viewModel::updatePurchaseCost,
                        label = { Text("Purchase Cost") },
                        placeholder = { Text("0.00") },
                        leadingIcon = { Text("$") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.purchaseCost.isNotEmpty() && uiState.purchaseCost.toDoubleOrNull() == null,
                        supportingText = if (uiState.purchaseCost.isNotEmpty() && uiState.purchaseCost.toDoubleOrNull() == null) {
                            { Text("Please enter a valid price") }
                        } else null
                    )

                    // Notes
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::updateNotes,
                        label = { Text("Notes") },
                        placeholder = { Text("Any additional notes...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = { viewModel.saveBottle(onNavigateBack) },
                        enabled = uiState.canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isEditMode) "Update Bottle" else "Add Bottle")
                    }
                }
            }
        }

        // Error Snackbar
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                // In a real app, you'd show a Snackbar here
                // For now, just clear the error
                viewModel.clearError()
            }
        }

        // Date Picker
        if (showDatePicker) {
            DatePickerDialog(
                onDateSelected = { date ->
                    viewModel.updatePurchaseDate(date)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandDropdown(
    selectedBrand: Brand?,
    brands: List<Brand>,
    onBrandSelected: (Brand) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedBrand?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Brand") },
            placeholder = { Text("Select a brand") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            brands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(brand.name) },
                    onClick = {
                        onBrandSelected(brand)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDropdown(
    selectedProduct: Product?,
    products: List<Product>,
    enabled: Boolean,
    onProductSelected: (Product) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedProduct?.name ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Product") },
            placeholder = { Text(if (enabled) "Select a product" else "Select a brand first") },
            trailingIcon = {
                if (enabled) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        if (enabled) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                products.forEach { product ->
                    DropdownMenuItem(
                        text = { Text(product.name) },
                        onClick = {
                            onProductSelected(product)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSelection(
    selectedStatus: BottleStatus,
    onStatusSelected: (BottleStatus) -> Unit
) {
    Column {
        Text(
            text = "Status",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BottleStatus.values().forEach { status ->
                FilterChip(
                    onClick = { onStatusSelected(status) },
                    label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    selected = selectedStatus == status
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDateField(
    selectedDate: Date?,
    onDateClick: () -> Unit
) {
    OutlinedTextField(
        value = selectedDate?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text("Purchase Date") },
        placeholder = { Text("Select date") },
        trailingIcon = {
            IconButton(onClick = onDateClick) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select date"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Date(millis))
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
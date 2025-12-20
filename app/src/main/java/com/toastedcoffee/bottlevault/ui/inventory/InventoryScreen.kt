// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/inventory/InventoryScreen.kt
package com.toastedcoffee.bottlevault.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.BottleWithProduct
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onAddBottleClick: () -> Unit,
    onBottleClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "BottleVault",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Filter bottles"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBottleClick
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add bottle"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            val currentFilter = uiState.selectedFilter // Use currentFilter instead of selectedFilter in this block
            if (currentFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by: ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilterChip(
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text(uiState.selectedFilter!!.name) },
                        selected = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.bottles.isEmpty() -> {
                    EmptyStateContent(
                        hasFilter = uiState.selectedFilter != null,
                        onAddBottleClick = onAddBottleClick,
                        onClearFilter = { viewModel.setStatusFilter(null) }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.bottles,
                            key = { it.bottle.id }
                        ) { bottleWithProduct ->
                            BottleCard(
                                bottleWithProduct = bottleWithProduct,
                                onCardClick = { onBottleClick(bottleWithProduct.bottle.id) },
                                onStatusClick = { status ->
                                    viewModel.updateBottleStatus(bottleWithProduct.bottle.id, status)
                                },
                                onDeleteClick = {
                                    viewModel.deleteBottle(bottleWithProduct.bottle)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Error handling
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                // Show snackbar or toast here if needed
                // For now, just clear the error after showing
                viewModel.clearError()
            }
        }

        // Filter dialog
        if (showFilterDialog) {
            FilterDialog(
                currentFilter = uiState.selectedFilter,
                onFilterSelected = { filter ->
                    viewModel.setStatusFilter(filter)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }
    }
}

@Composable
private fun BottleCard(
    bottleWithProduct: BottleWithProduct,
    onCardClick: () -> Unit,
    onStatusClick: (BottleStatus) -> Unit,
    onDeleteClick: () -> Unit
) {
    val bottle = bottleWithProduct.bottle
    val product = bottleWithProduct.product

    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Product name and brand
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.brand.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status and details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status chip
                StatusChip(
                    status = bottle.status,
                    onClick = {
                        // Cycle through statuses
                        val nextStatus = when (bottle.status) {
                            BottleStatus.AVAILABLE -> BottleStatus.OPENED
                            BottleStatus.OPENED -> BottleStatus.CONSUMED
                            BottleStatus.CONSUMED -> BottleStatus.AVAILABLE
                            BottleStatus.UNOPENED -> BottleStatus.OPENED
                            BottleStatus.EMPTY -> BottleStatus.UNOPENED
                        }
                        onStatusClick(nextStatus)
                    }
                )

                // Purchase date
                bottle.purchaseDate?.let { date ->
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Purchase location and cost
            if (bottle.purchaseLocation != null || bottle.purchaseCost != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bottle.purchaseLocation?.let { location ->
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    bottle.purchaseCost?.let { cost ->
                        Text(
                            text = "$%.2f".format(cost),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: BottleStatus,
    onClick: () -> Unit
) {
    val (text, color) = when (status) {
        BottleStatus.AVAILABLE -> "Available" to MaterialTheme.colorScheme.primary
        BottleStatus.OPENED -> "Opened" to MaterialTheme.colorScheme.tertiary
        BottleStatus.CONSUMED -> "Consumed" to MaterialTheme.colorScheme.outline
        BottleStatus.UNOPENED -> "Unopened" to MaterialTheme.colorScheme.secondary
        BottleStatus.EMPTY -> "Empty" to MaterialTheme.colorScheme.error
    }

    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = color
        )
    )
}

@Composable
private fun EmptyStateContent(
    hasFilter: Boolean,
    onAddBottleClick: () -> Unit,
    onClearFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasFilter) "No bottles match your filter" else "No bottles in your vault yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (hasFilter) {
            Button(onClick = onClearFilter) {
                Text("Clear Filter")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = onAddBottleClick) {
            Text("Add Your First Bottle")
        }
    }
}

@Composable
private fun FilterDialog(
    currentFilter: BottleStatus?,
    onFilterSelected: (BottleStatus?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Status") },
        text = {
            Column {
                FilterOption(
                    text = "All Bottles",
                    isSelected = currentFilter == null,
                    onClick = { onFilterSelected(null) }
                )
                BottleStatus.values().forEach { status ->
                    FilterOption(
                        text = status.name.lowercase().replaceFirstChar { it.uppercase() },
                        isSelected = currentFilter == status,
                        onClick = { onFilterSelected(status) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun FilterOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
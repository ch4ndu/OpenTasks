package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_list
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.close
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_check
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_inbox
import opentasks.composeapp.generated.resources.ic_list
import opentasks.composeapp.generated.resources.list_name
import opentasks.composeapp.generated.resources.move_to
import opentasks.composeapp.generated.resources.ok
import opentasks.composeapp.generated.resources.search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerBottomSheet(
    sheetState: SheetState,
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategorySelected: (Category) -> Unit,
    onAddCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    showTitle: Boolean = true,
    showSearch: Boolean = true,
) {
    val dimens = OpenTasksTheme.dimens
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        CategoryPickerContent(
            categories = filteredCategories,
            selectedCategoryId = selectedCategoryId,
            showTitle = showTitle,
            showSearch = showSearch,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onCategorySelected = onCategorySelected,
            onAddCategoryClick = { showAddDialog = true },
            onDismiss = onDismiss,
        )
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                onAddCategory(name)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun CategoryPickerContent(
    categories: List<Category>,
    selectedCategoryId: Long,
    showTitle: Boolean,
    showSearch: Boolean = true,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (Category) -> Unit,
    onAddCategoryClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimens.paddingXXLarge),
    ) {
        // Header: X button + "Move to"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimens.paddingSmall,
                    end = dimens.paddingXLarge,
                    top = dimens.paddingMedium
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showTitle) {
                Text(
                    text = stringResource(Res.string.move_to),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Search bar
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.search),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingMedium),
                shape = RoundedCornerShape(dimens.cornerXLarge),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

        Spacer(Modifier.height(dimens.spacerSmall))

        // List items
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(categories, key = { it.id }) { category ->
                val isSelected = category.id == selectedCategoryId
                CategoryPickerRow(
                    category = category,
                    isSelected = isSelected,
                    onClick = { onCategorySelected(category) },
                )
            }

            // Add Category row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddCategoryClick)
                        .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(dimens.iconXLarge),
                    )
                    Spacer(Modifier.width(dimens.spacerXXLarge))
                    Text(
                        text = stringResource(Res.string.add_list),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconRes = if (category.icon == "inbox") {
            Res.drawable.ic_inbox
        } else {
            Res.drawable.ic_list
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimens.iconXLarge),
        )
        Spacer(Modifier.width(dimens.spacerXXLarge))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(dimens.touchTargetSmall),
            )
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.add_list),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = {
                    Text(stringResource(Res.string.list_name))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
            ) {
                Text(
                    text = stringResource(Res.string.ok),
                    color = PrimaryBlue,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
@Preview
private fun CategoryPickerPreview() {
    val sampleCategories = listOf(
        Category(id = 1, name = "Inbox", icon = "inbox"),
        Category(id = 2, name = "Work"),
        Category(id = 3, name = "Personal"),
    )
    OpenTasksTheme {
        CategoryPickerContent(
            categories = sampleCategories,
            selectedCategoryId = 1L,
            showTitle = true,
            searchQuery = "",
            onSearchQueryChange = {},
            onCategorySelected = {},
            onAddCategoryClick = {},
            onDismiss = {},
        )
    }
}

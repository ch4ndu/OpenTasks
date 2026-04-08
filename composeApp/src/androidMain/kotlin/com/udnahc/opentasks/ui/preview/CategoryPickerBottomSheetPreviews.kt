package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.ui.screens.CategoryPickerContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun CategoryPickerPreview() {
    val sampleCategories = listOf(
        Category(id = "1", name = "Inbox", icon = "inbox"),
        Category(id = "2", name = "Work"),
        Category(id = "3", name = "Personal"),
    )
    OpenTasksTheme {
        CategoryPickerContent(
            categories = sampleCategories,
            selectedCategoryId = "1",
            showTitle = true,
            searchQuery = "",
            onSearchQueryChange = {},
            onCategorySelected = {},
            onAddCategoryClick = {},
            onDismiss = {},
        )
    }
}

package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Shared category-picker state for the owning screen ViewModel.
 *
 * Each delegate is scoped to its caller so search state cannot leak between
 * task-list and task-form picker instances.
 */
class CategoryPickerDelegate(
    observeAllCategories: ObserveAllCategoriesUseCase,
    private val addCategoryAction: AddCategoryAction,
    private val scope: CoroutineScope,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val searchQuery = MutableStateFlow("")
    val categorySearchQuery: StateFlow<String> = searchQuery

    val filteredCategories: StateFlow<List<Category>> =
        combine(categories, searchQuery) { categories, query ->
            if (query.isBlank()) categories
            else categories.filter { it.name.contains(query, ignoreCase = true) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        scope,
    )

    fun setCategorySearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addCategory(name: String) {
        mutationLauncher.launch { addCategoryAction(name) }
    }
}

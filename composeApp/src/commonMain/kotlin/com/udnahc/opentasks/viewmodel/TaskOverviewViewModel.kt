package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskOverviewCountsUseCase
import com.udnahc.opentasks.domain.usecase.task.taskOverviewSmartFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class SmartViewCount(val filter: TaskListFilter, val count: Int)

data class ListCount(val category: Category, val count: Int)

data class TaskOverviewState(
    val shortlistCount: Int = 0,
    val smartViews: List<SmartViewCount> = taskOverviewSmartFilters.map { SmartViewCount(it, 0) },
    val lists: List<ListCount> = emptyList(),
)

class TaskOverviewViewModel(
    observeTaskOverviewCounts: ObserveTaskOverviewCountsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    addCategoryAction: AddCategoryAction,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    private val categoryPicker = CategoryPickerDelegate(
        observeAllCategories,
        addCategoryAction,
        viewModelScope,
        accountBoundaryExecutor,
    )

    val state: StateFlow<TaskOverviewState> =
        combine(observeTaskOverviewCounts(), categoryPicker.categories) { counts, categories ->
            TaskOverviewState(
                shortlistCount = counts[TaskListFilter.Shortlist] ?: 0,
                smartViews = taskOverviewSmartFilters.map { SmartViewCount(it, counts[it] ?: 0) },
                lists = categories.map { category ->
                    ListCount(category, counts[TaskListFilter.Category(category.id)] ?: 0)
                },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskOverviewState())

    fun addCategory(name: String) {
        categoryPicker.addCategory(name)
    }
}

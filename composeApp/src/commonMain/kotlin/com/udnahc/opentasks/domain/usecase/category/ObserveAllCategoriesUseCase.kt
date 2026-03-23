package com.udnahc.opentasks.domain.usecase.category

import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllCategoriesUseCase(private val repository: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = repository.getAllCategories()
}

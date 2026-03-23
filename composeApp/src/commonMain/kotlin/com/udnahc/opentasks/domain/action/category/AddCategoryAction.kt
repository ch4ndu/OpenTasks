package com.udnahc.opentasks.domain.action.category

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.repository.CategoryRepository

class AddCategoryAction(private val repository: CategoryRepository) {
    suspend operator fun invoke(name: String) {
        repository.insert(
            Category(
                name = name,
                createdAt = utcNow(),
            )
        )
    }
}

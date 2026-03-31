package com.udnahc.opentasks.domain.action.category

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.repository.CategoryRepository
import org.lighthousegames.logging.logging

private val log = logging("AddCategoryAction")

class AddCategoryAction(private val repository: CategoryRepository) {
    suspend operator fun invoke(name: String) {
        log.d { "Adding category: '$name'" }
        val now = localNow()
        repository.insert(
            Category(
                name = name,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}

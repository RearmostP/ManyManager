package com.example.manymanager

import android.os.Bundle
import com.example.manymanager.domain.Category
import com.example.manymanager.domain.DataError
import com.example.manymanager.domain.DataResult

sealed interface CategoryListState {
    data object Loading : CategoryListState
    data class Ready(val categories: List<Category>) : CategoryListState
    data class Failed(val error: DataError) : CategoryListState
}

/** Form/operation state is separate from the observed list and contains no View references. */
data class CategoryCreationState(
    val fromExpense: Boolean,
    val name: String = "",
    val submitting: Boolean = false,
    val result: DataResult<Category>? = null,
    val interrupted: Boolean = false,
) {
    fun toBundle() = Bundle().apply {
        putBoolean("fromExpense", fromExpense)
        putString("name", name)
        putBoolean("submitting", submitting)
        putBoolean("interrupted", interrupted)
        (result as? DataResult.Success)?.value?.let {
            putString("createdId", it.id)
            putString("createdOwner", it.calculatorId)
            putString("createdName", it.name)
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle): CategoryCreationState {
            val category = bundle.getString("createdId")?.let { id ->
                Category(checkNotNull(bundle.getString("createdOwner")), id,
                    bundle.getString("createdName").orEmpty())
            }
            return CategoryCreationState(
                fromExpense = bundle.getBoolean("fromExpense"),
                name = bundle.getString("name").orEmpty(),
                result = category?.let { DataResult.Success(it) },
                // A new process must never silently repeat a possibly committed request.
                interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted"),
            )
        }
    }
}

/** Same localized status and retry wording in category screen, summary, and expense choices. */
fun CategoryListState.statusMessage(): Int? = when (this) {
    CategoryListState.Loading -> R.string.categories_loading
    is CategoryListState.Failed -> R.string.categories_load_error
    is CategoryListState.Ready -> if (categories.isEmpty()) R.string.no_categories else null
}

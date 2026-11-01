package com.example.manymanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.manymanager.data.ManyManagerRepository
import com.example.manymanager.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PersistentDataState {
    data object Loading : PersistentDataState
    data class Ready(val snapshot: BudgetSnapshot) : PersistentDataState
    data class Failed(val error: DataError) : PersistentDataState
}

/** Category screens use the focused stream; financial screens still use BudgetViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class PersistentDataViewModel(private val repository: ManyManagerRepository) : ViewModel() {
    private data class Selection(val period: BudgetPeriod? = null, val refresh: Long = 0)
    private val selection = MutableStateFlow(Selection())

    val state: StateFlow<PersistentDataState> = selection.flatMapLatest { selected ->
        repository.observeSnapshot(selected.period).map { result ->
            when (result) {
                is DataResult.Success -> PersistentDataState.Ready(result.value)
                is DataResult.Failure -> PersistentDataState.Failed(result.error)
            }
        }.onStart { emit(PersistentDataState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersistentDataState.Loading)

    private val categoryRefresh = MutableStateFlow(0L)
    val categories: StateFlow<CategoryListState> = categoryRefresh.flatMapLatest {
        repository.observeCategories().map { result ->
            when (result) {
                is DataResult.Success -> CategoryListState.Ready(result.value)
                is DataResult.Failure -> CategoryListState.Failed(result.error)
            }
        }.onStart { emit(CategoryListState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryListState.Loading)

    private val creation = MutableStateFlow<CategoryCreationState?>(null)
    val categoryCreation: StateFlow<CategoryCreationState?> = creation
    fun retryCategories() { categoryRefresh.update { it + 1 } }

    fun beginCategoryCreation(fromExpense: Boolean) {
        if (creation.value == null) creation.value = CategoryCreationState(fromExpense)
    }

    fun restoreCategoryCreation(restored: CategoryCreationState) {
        if (creation.value == null) creation.value = restored
    }

    fun updateCategoryName(name: String) {
        creation.update { current ->
            if (current == null || current.submitting || current.result is DataResult.Success || current.name == name) current
            else current.copy(name = name, result = null)
        }
    }

    fun cancelCategoryCreation() {
        creation.update { current ->
            if (current?.submitting == true || current?.result is DataResult.Success) current else null
        }
    }

    fun acknowledgeCategoryCreation() {
        if (creation.value?.result is DataResult.Success) creation.value = null
    }

    /** Synchronous busy guard prevents two taps, even before the dialog renders disabled buttons. */
    fun submitCategoryCreation(): Job? {
        val current = creation.value ?: return null
        if (current.submitting || current.result is DataResult.Success) return null
        creation.value = current.copy(submitting = true, result = null, interrupted = false)
        return viewModelScope.launch {
            val result = repository.createCategory(current.name)
            currentCoroutineContext().ensureActive()
            creation.value = current.copy(result = result, interrupted = false)
        }
    }

    /** Null follows today. Call refresh on resume/date changes when screens are migrated. */
    fun selectPeriod(period: BudgetPeriod?) { selection.update { it.copy(period = period) } }
    fun refresh() { selection.update { it.copy(refresh = it.refresh + 1) } }

}

class PersistentDataViewModelFactory(
    private val repository: ManyManagerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PersistentDataViewModel::class.java) { "Unsupported ViewModel type" }
        return PersistentDataViewModel(repository) as T
    }
}

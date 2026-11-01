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

/** Prepared for staged migration. MainActivity still constructs the temporary BudgetViewModel. */
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

    /** Null follows today. Call refresh on resume/date changes when screens are migrated. */
    fun selectPeriod(period: BudgetPeriod?) { selection.update { it.copy(period = period) } }
    fun refresh() { selection.update { it.copy(refresh = it.refresh + 1) } }

    /** One-time outcome is separate from observable data; no replaying success event in state. */
    fun createCategory(name: String, onResult: (DataResult<Category>) -> Unit): Job =
        viewModelScope.launch {
            val result = repository.createCategory(name)
            currentCoroutineContext().ensureActive()
            onResult(result)
        }
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

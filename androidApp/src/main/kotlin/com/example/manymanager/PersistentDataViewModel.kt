package com.example.manymanager

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.manymanager.data.ManyManagerRepository
import com.example.manymanager.domain.*
import java.time.LocalDate
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

data class SourceCreationState(
    val period: BudgetPeriod,
    val name: String = "",
    val initialAmountText: String = "0",
    val submitting: Boolean = false,
    val result: DataResult<BudgetSource>? = null,
    val interrupted: Boolean = false,
) {
    fun toBundle() = Bundle().apply {
        putLong("startEpochDay", period.startEpochDay)
        putLong("endExclusiveEpochDay", period.endExclusiveEpochDay)
        putString("name", name)
        putString("initialAmountText", initialAmountText)
        putBoolean("submitting", submitting)
        putBoolean("interrupted", interrupted)
        (result as? DataResult.Success)?.value?.let {
            putString("createdId", it.id)
            putString("createdOwner", it.calculatorId)
            putString("createdName", it.name)
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle): SourceCreationState? {
            if (!bundle.containsKey("startEpochDay") || !bundle.containsKey("endExclusiveEpochDay")) return null
            val startEpoch = bundle.getLong("startEpochDay")
            val endEpoch = bundle.getLong("endExclusiveEpochDay")
            val period = BudgetPeriod(LocalDate.ofEpochDay(startEpoch), LocalDate.ofEpochDay(endEpoch))
            val source = bundle.getString("createdId")?.let { id ->
                BudgetSource(checkNotNull(bundle.getString("createdOwner")), id, bundle.getString("createdName").orEmpty())
            }
            return SourceCreationState(
                period = period,
                name = bundle.getString("name").orEmpty(),
                initialAmountText = bundle.getString("initialAmountText", "0"),
                result = source?.let { DataResult.Success(it) },
                interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted"),
            )
        }
    }
}

data class SourceEditingState(
    val source: BudgetSource,
    val period: BudgetPeriod,
    val currentAllocationMinor: Long,
    val name: String = source.name,
    val adjustmentText: String = "",
    val isAdd: Boolean = true,
    val submitting: Boolean = false,
    val previewResult: DataResult<BudgetAdjustmentPreview>? = null,
    val submitResult: DataResult<BudgetAdjustmentPreview>? = null,
    val interrupted: Boolean = false,
) {
    fun toBundle() = Bundle().apply {
        putString("sourceId", source.id)
        putString("sourceOwner", source.calculatorId)
        putString("sourceName", source.name)
        putLong("startEpochDay", period.startEpochDay)
        putLong("endExclusiveEpochDay", period.endExclusiveEpochDay)
        putLong("currentAllocationMinor", currentAllocationMinor)
        putString("name", name)
        putString("adjustmentText", adjustmentText)
        putBoolean("isAdd", isAdd)
        (submitResult as? DataResult.Success)?.value?.let {
            putLong("committedCurrent", it.currentAllocatedMinor)
            putLong("committedDelta", it.deltaMinor)
            putLong("committedResult", it.resultingAllocatedMinor)
        }
        putBoolean("submitting", submitting)
        putBoolean("interrupted", interrupted)
    }

    companion object {
        fun fromBundle(bundle: Bundle): SourceEditingState? {
            val sourceId = bundle.getString("sourceId") ?: return null
            if (!bundle.containsKey("startEpochDay") || !bundle.containsKey("endExclusiveEpochDay")) return null
            val startEpoch = bundle.getLong("startEpochDay")
            val endEpoch = bundle.getLong("endExclusiveEpochDay")
            val source = BudgetSource(bundle.getString("sourceOwner").orEmpty(), sourceId, bundle.getString("sourceName").orEmpty())
            val period = BudgetPeriod(LocalDate.ofEpochDay(startEpoch), LocalDate.ofEpochDay(endEpoch))
            return SourceEditingState(
                source = source,
                period = period,
                currentAllocationMinor = bundle.getLong("currentAllocationMinor"),
                name = bundle.getString("name").orEmpty(),
                adjustmentText = bundle.getString("adjustmentText").orEmpty(),
                isAdd = bundle.getBoolean("isAdd", true),
                submitResult = if (bundle.containsKey("committedResult")) DataResult.Success(
                    BudgetAdjustmentPreview(source.id, period, bundle.getLong("committedCurrent"),
                        bundle.getLong("committedDelta"), bundle.getLong("committedResult"))
                ) else null,
                interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted"),
            )
        }
    }
}

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

    // Home always follows today; management may retain an explicit historical selection.
    private val activeRefresh = MutableStateFlow(0L)
    val activeBudget: StateFlow<PersistentDataState> = activeRefresh.flatMapLatest {
        repository.observeSnapshot().map { result ->
            when (result) {
                is DataResult.Success -> PersistentDataState.Ready(result.value)
                is DataResult.Failure -> PersistentDataState.Failed(result.error)
            }
        }.onStart { emit(PersistentDataState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersistentDataState.Loading)

    private val sourceRefresh = MutableStateFlow(0L)
    val sources: StateFlow<SourceListState> = sourceRefresh.flatMapLatest {
        repository.observeSources().map { result ->
            when (result) {
                is DataResult.Success -> SourceListState.Ready(result.value)
                is DataResult.Failure -> SourceListState.Failed(result.error)
            }
        }.onStart { emit(SourceListState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourceListState.Loading)

    fun retrySources() { sourceRefresh.update { it + 1 } }

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

    private val sourceCreation = MutableStateFlow<SourceCreationState?>(null)
    val sourceCreationState: StateFlow<SourceCreationState?> = sourceCreation

    private val sourceEditing = MutableStateFlow<SourceEditingState?>(null)
    val sourceEditingState: StateFlow<SourceEditingState?> = sourceEditing

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

    fun beginSourceCreation(period: BudgetPeriod) {
        if (sourceCreation.value == null) {
            sourceCreation.value = SourceCreationState(period = period)
        }
    }

    fun restoreSourceCreation(restored: SourceCreationState) {
        if (sourceCreation.value == null) sourceCreation.value = restored
    }

    fun updateSourceCreationName(name: String) {
        sourceCreation.update { current ->
            if (current == null || current.submitting || current.interrupted || current.result is DataResult.Success || current.name == name) current
            else current.copy(name = name, result = null)
        }
    }

    fun updateSourceCreationAmount(amountText: String) {
        sourceCreation.update { current ->
            if (current == null || current.submitting || current.interrupted || current.result is DataResult.Success || current.initialAmountText == amountText) current
            else current.copy(initialAmountText = amountText, result = null)
        }
    }

    fun cancelSourceCreation() {
        sourceCreation.value?.takeIf { it.interrupted }?.let {
            selectPeriod(it.period)
            refresh()
        }
        sourceCreation.update { current ->
            if (current?.submitting == true || current?.result is DataResult.Success) current else null
        }
    }

    fun acknowledgeSourceCreation() {
        if (sourceCreation.value?.result is DataResult.Success) sourceCreation.value = null
    }

    fun submitSourceCreation(): Job? {
        val current = sourceCreation.value ?: return null
        if (current.submitting || current.interrupted || current.result is DataResult.Success) return null
        val parsed = BudgetAmounts.parse(current.initialAmountText)
        if (parsed == null) {
            sourceCreation.value = current.copy(result = DataResult.Failure(DataError.AmountOverflow))
            return null
        }
        sourceCreation.value = current.copy(submitting = true, result = null, interrupted = false)
        return viewModelScope.launch {
            val result = repository.createSourceWithInitialAllocation(current.name, current.period, parsed)
            currentCoroutineContext().ensureActive()
            sourceCreation.value = current.copy(result = result, interrupted = false)
        }
    }

    private var previewJob: Job? = null
    private var previewRevision = 0L

    fun beginSourceEditing(source: BudgetSource, period: BudgetPeriod, currentAllocationMinor: Long) {
        if (sourceEditing.value == null) {
            val state = SourceEditingState(source, period, currentAllocationMinor)
            sourceEditing.value = state
            updateSourceEditingAdjustment("", isAdd = true)
        }
    }

    fun restoreSourceEditing(restored: SourceEditingState) {
        if (sourceEditing.value == null) {
            sourceEditing.value = restored
            if (!restored.interrupted && restored.submitResult !is DataResult.Success)
                updateSourceEditingAdjustment(restored.adjustmentText, restored.isAdd)
        }
    }

    fun updateSourceEditingName(name: String) {
        sourceEditing.update { current ->
            if (current == null || current.submitting || current.interrupted || current.submitResult is DataResult.Success || current.name == name) current
            else current.copy(name = name, submitResult = null)
        }
    }

    fun updateSourceEditingAdjustment(text: String, isAdd: Boolean) {
        val current = sourceEditing.value ?: return
        if (current.submitting || current.interrupted || current.submitResult is DataResult.Success) return
        val revision = ++previewRevision
        val updated = current.copy(adjustmentText = text, isAdd = isAdd, submitResult = null, previewResult = null)
        sourceEditing.value = updated

        previewJob?.cancel()
        val parsed = if (text.isBlank()) 0L else BudgetAmounts.parse(text)
        if (parsed == null) {
            sourceEditing.update { it?.copy(previewResult = DataResult.Failure(DataError.AmountOverflow)) }
            return
        }
        val delta = if (isAdd) parsed else -parsed
        previewJob = viewModelScope.launch {
            val result = repository.previewBudgetAdjustment(current.source.id, current.period, delta)
            ensureActive()
            sourceEditing.update {
                if (revision == previewRevision && it?.source?.id == current.source.id)
                    it.copy(previewResult = result) else it
            }
        }
    }

    fun cancelSourceEditing() {
        sourceEditing.value?.takeIf { it.interrupted }?.let {
            selectPeriod(it.period)
            refresh()
        }
        ++previewRevision
        previewJob?.cancel()
        previewJob = null
        sourceEditing.update { current ->
            if (current?.submitting == true || current?.submitResult is DataResult.Success) current else null
        }
    }

    fun acknowledgeSourceEditing() {
        if (sourceEditing.value?.submitResult is DataResult.Success) sourceEditing.value = null
    }

    fun submitSourceEditing(): Job? {
        val current = sourceEditing.value ?: return null
        if (current.submitting || current.interrupted || current.submitResult is DataResult.Success) return null
        val parsed = if (current.adjustmentText.isBlank()) 0L else BudgetAmounts.parse(current.adjustmentText)
        val preview = (current.previewResult as? DataResult.Success)?.value
        if (current.name.isBlank() || parsed == null || preview == null ||
            preview.resultingAllocatedMinor < 0) return null
        val delta = if (current.isAdd) parsed else -parsed
        sourceEditing.value = current.copy(submitting = true, submitResult = null, interrupted = false)
        return viewModelScope.launch {
            val result = repository.updateSourceAndAdjustAllocation(
                current.source.id, current.name, current.period, delta
            )
            currentCoroutineContext().ensureActive()
            sourceEditing.value = current.copy(submitResult = result, interrupted = false)
        }
    }

    private var navigationRevision = 0L

    fun selectPreviousPeriod() {
        val current = (state.value as? PersistentDataState.Ready)?.snapshot?.period ?: return
        val revision = ++navigationRevision
        viewModelScope.launch {
            val prevResult = repository.periodContaining(current.start.minusDays(1))
            if (revision == navigationRevision && prevResult is DataResult.Success) {
                selectPeriod(prevResult.value)
            }
        }
    }

    fun selectNextPeriod() {
        val current = (state.value as? PersistentDataState.Ready)?.snapshot?.period ?: return
        val revision = ++navigationRevision
        viewModelScope.launch {
            val nextResult = repository.periodContaining(current.endExclusive)
            if (revision == navigationRevision && nextResult is DataResult.Success) {
                selectPeriod(nextResult.value)
            }
        }
    }

    /** Null follows today. Call refresh on resume/date changes when screens are migrated. */
    val selectedPeriod: BudgetPeriod? get() = selection.value.period
    fun selectPeriod(period: BudgetPeriod?) {
        ++navigationRevision
        selection.update { it.copy(period = period) }
    }
    fun refresh() {
        selection.update { it.copy(refresh = it.refresh + 1) }
        activeRefresh.update { it + 1 }
        retrySources()
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

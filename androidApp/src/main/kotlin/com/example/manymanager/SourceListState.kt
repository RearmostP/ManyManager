package com.example.manymanager

import com.example.manymanager.domain.BudgetSource
import com.example.manymanager.domain.DataError

sealed interface SourceListState {
    data object Loading : SourceListState
    data class Ready(val sources: List<BudgetSource>) : SourceListState
    data class Failed(val error: DataError) : SourceListState
}

fun SourceListState.statusMessage(): Int? = when (this) {
    SourceListState.Loading -> R.string.sources_loading
    is SourceListState.Failed -> R.string.sources_load_error
    is SourceListState.Ready -> if (sources.isEmpty()) R.string.no_payment_methods else null
}

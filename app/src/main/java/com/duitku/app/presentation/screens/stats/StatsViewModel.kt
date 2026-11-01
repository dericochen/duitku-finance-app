package com.duitku.app.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.StatsData
import com.duitku.app.domain.model.StatsMode
import com.duitku.app.domain.model.StatsRequest
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class StatsUiState(
    val isLoading: Boolean = true,
    val request: StatsRequest = StatsRequest(),
    val data: StatsData? = null,
    val currencyCode: String = "IDR",
    val error: String? = null,
) {
    /** Navigasi ke periode setelah hari ini tidak berguna. */
    val canGoNext: Boolean
        get() = data != null && request.mode in setOf(StatsMode.WEEK, StatsMode.MONTH, StatsMode.YEAR) &&
            data.range.endInclusive.isBefore(LocalDate.now())
    val canNavigate: Boolean get() = request.mode in setOf(StatsMode.WEEK, StatsMode.MONTH, StatsMode.YEAR)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stats: StatsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val request = MutableStateFlow(StatsRequest())

    private val data = request.flatMapLatest { req ->
        stats.observe(req).map { Result.success(it) }.catch { emit(Result.failure(it)) }
    }

    val uiState: StateFlow<StatsUiState> = combine(request, data, settingsRepository.settings) { req, result, settings ->
        result.fold(
            onSuccess = { StatsUiState(isLoading = false, request = req, data = it, currencyCode = settings.currencyCode) },
            onFailure = {
                StatsUiState(isLoading = false, request = req, currencyCode = settings.currencyCode, error = "Statistik gagal dimuat")
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
    fun setMode(mode: StatsMode) = request.update { it.copy(mode = mode, anchor = LocalDate.now()) }

    fun previous() = shift(-1)
    fun next() = shift(1)

    private fun shift(direction: Long) = request.update { r ->
        r.copy(
            anchor = when (r.mode) {
                StatsMode.WEEK -> r.anchor.plusWeeks(direction)
                StatsMode.MONTH -> r.anchor.plusMonths(direction)
                StatsMode.YEAR -> r.anchor.plusYears(direction)
                else -> r.anchor
            },
        )
    }

    fun setCustomRange(from: LocalDate?, to: LocalDate?) = request.update { it.copy(mode = StatsMode.CUSTOM, customFrom = from, customTo = to) }

    fun setMonthsBack(months: Int) = request.update { it.copy(monthsBack = months) }
}

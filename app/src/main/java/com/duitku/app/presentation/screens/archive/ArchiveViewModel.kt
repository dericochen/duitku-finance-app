package com.duitku.app.presentation.screens.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.AuditEntry
import com.duitku.app.domain.model.StatementSummary
import com.duitku.app.domain.model.VerifyResult
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArchiveUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val year: Int? = null,
    val years: List<Int> = emptyList(),
    val items: List<StatementSummary> = emptyList(),
    val totalCount: Int = 0,
    val currencyCode: String = "IDR",
) {
    val byYear: List<Pair<Int, List<StatementSummary>>>
        get() = items.groupBy { it.periodYm / 100 }.toList().sortedByDescending { it.first }
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val statements: StatementRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val year = MutableStateFlow<Int?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<ArchiveUiState> = combine(
        statements.observeSummaries(), query, year, settingsRepository.settings,
    ) { all, q, y, settings ->
        val needle = q.trim().lowercase()
        val filtered = all.filter { s ->
            (y == null || s.periodYm / 100 == y) &&
                (needle.isEmpty() ||
                    s.documentNumber.lowercase().contains(needle) ||
                    DateFormats.monthYear(s.periodYm.toYearMonth()).lowercase().contains(needle))
        }
        ArchiveUiState(
            isLoading = false, query = q, year = y,
            years = all.map { it.periodYm / 100 }.distinct().sortedDescending(),
            items = filtered, totalCount = all.size, currencyCode = settings.currencyCode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    fun onQueryChange(value: String) {
        query.value = value.take(60)
    }

    fun selectYear(value: Int?) {
        year.update { if (it == value) null else value }
    }

    fun verify(id: Long) {
        viewModelScope.launch {
            _messages.send(
                when (val r = statements.verify(id)) {
                    is VerifyResult.Valid -> "Data utuh (SHA-256 ${r.checksum.take(12)}...)"
                    is VerifyResult.Tampered -> "PERINGATAN: checksum tidak cocok, data arsip mungkin berubah"
                    VerifyResult.NotFound -> "Arsip tidak ditemukan"
                },
            )
        }
    }
}

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    statements: StatementRepository,
) : ViewModel() {
    val entries: StateFlow<List<AuditEntry>?> = statements.observeAudit(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

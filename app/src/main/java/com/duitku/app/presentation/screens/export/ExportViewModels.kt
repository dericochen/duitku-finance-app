package com.duitku.app.presentation.screens.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.ExportProgress
import com.duitku.app.domain.model.ExportResult
import com.duitku.app.domain.model.PdfTheme
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.ExportRecord
import com.duitku.app.domain.repository.ExportRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val isLoading: Boolean = true,
    val kind: ExportKind = ExportKind.MONTHLY,
    val format: ExportFormat = ExportFormat.PDF,
    val periods: List<Int> = emptyList(),
    val fromYm: Int = 0,
    val toYm: Int = 0,
    val wallets: List<Wallet> = emptyList(),
    val walletId: Long? = null,
    val includeCharts: Boolean = true,
    val theme: PdfTheme = PdfTheme.CLASSIC,
    val note: String = "",
    val bankInputs: Map<Long, String> = emptyMap(),
    val progress: ExportProgress? = null,
    val result: ExportResult? = null,
    val error: String? = null,
) {
    val isRunning: Boolean get() = progress != null
    val canChooseWallet: Boolean get() = kind == ExportKind.MONTHLY
    val isPdf: Boolean get() = format == ExportFormat.PDF
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val export: ExportRepository,
    private val statements: StatementRepository,
    walletRepository: WalletRepository,
    transactions: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val argPeriod = savedStateHandle.get<Int>(Screen.ExportCenter.ARG_PERIOD)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val day = settingsRepository.current().monthStartDay
            val current = PeriodCalculator.periodOf(LocalDate.now(), day)
            val oldest = transactions.observeOldestAt().first()?.let { PeriodCalculator.periodOfMillis(it, day) } ?: current
            val first = maxOf(oldest, current.minusMonths(59))
            val list = PeriodCalculator.monthsBetween(first, current).map { it.toKey() }
            val wallets = walletRepository.observeWallets().first().filter { !it.isArchived }
            val start = argPeriod?.takeIf { it in list } ?: current.minusMonths(1).toKey().takeIf { it in list } ?: current.toKey()
            _state.update {
                it.copy(isLoading = false, periods = list, fromYm = start, toYm = start, wallets = wallets, note = existingNote(start))
            }
        }
    }

    private suspend fun existingNote(ym: Int): String = statements.getActive(ym)?.userNote.orEmpty()

    fun setKind(kind: ExportKind) = _state.update { s ->
        when (kind) {
            ExportKind.MONTHLY -> s.copy(kind = kind, toYm = s.fromYm, result = null)
            ExportKind.RANGE -> s.copy(kind = kind, walletId = null, result = null)
            ExportKind.ANNUAL -> {
                // 12 bulan berakhir pada periode terakhir yang tersedia.
                val last = s.periods.lastOrNull() ?: s.toYm
                val from = maxOf(s.periods.firstOrNull() ?: last, last.toYearMonth().minusMonths(11).toKey())
                s.copy(kind = kind, walletId = null, fromYm = from, toYm = last, result = null)
            }
        }
    }

    fun setFormat(format: ExportFormat) = _state.update { it.copy(format = format, result = null) }

    fun setFrom(ym: Int) {
        _state.update { s -> s.copy(fromYm = ym, toYm = if (s.kind == ExportKind.MONTHLY || s.toYm < ym) ym else s.toYm, result = null) }
        if (_state.value.kind == ExportKind.MONTHLY) viewModelScope.launch { _state.update { it.copy(note = existingNote(ym)) } }
    }

    fun setTo(ym: Int) = _state.update { s -> s.copy(toYm = ym, fromYm = if (s.fromYm > ym) ym else s.fromYm, result = null) }
    fun setWallet(id: Long?) = _state.update { it.copy(walletId = id, result = null) }
    fun setCharts(v: Boolean) = _state.update { it.copy(includeCharts = v) }
    fun setTheme(t: PdfTheme) = _state.update { it.copy(theme = t) }
    fun setNote(v: String) = _state.update { it.copy(note = v.take(500)) }
    fun setBank(walletId: Long, v: String) = _state.update { it.copy(bankInputs = it.bankInputs + (walletId to v.filter(Char::isDigit).take(12))) }

    fun start() {
        val s = _state.value
        if (s.isRunning) return
        val bank = s.bankInputs.mapNotNull { (id, text) -> MoneyFormatter.parse(text)?.let { id to it } }.toMap()
        val options = ExportOptions(
            format = s.format, kind = s.kind, fromPeriodYm = s.fromYm, toPeriodYm = s.toYm, walletId = s.walletId,
            includeCharts = s.includeCharts, theme = s.theme, bankBalances = bank, userNote = s.note.ifBlank { null },
        )
        _state.update { it.copy(progress = ExportProgress(0, "Memulai"), result = null, error = null) }
        viewModelScope.launch {
            try {
                val result = export.export(options) { p -> _state.update { st -> st.copy(progress = p) } }
                _state.update { it.copy(progress = null, result = result) }
            } catch (e: Exception) {
                _state.update { it.copy(progress = null, error = e.message ?: "Ekspor gagal") }
            }
        }
    }

    fun saveTo(uri: String) {
        val id = _state.value.result?.historyId ?: return
        viewModelScope.launch {
            _events.send(if (export.copyTo(id, uri)) "Berkas disimpan" else "Gagal menyimpan berkas")
        }
    }
}

data class HistoryUiState(val isLoading: Boolean = true, val items: List<ExportRecord> = emptyList())

@HiltViewModel
class ExportHistoryViewModel @Inject constructor(
    private val export: ExportRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> = kotlinx.coroutines.flow.flow {
        export.observeHistory().collect { emit(HistoryUiState(false, it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun delete(record: ExportRecord) {
        viewModelScope.launch { export.deleteRecord(record.id) }
    }

    fun saveTo(id: Long, uri: String) {
        viewModelScope.launch { _messages.send(if (export.copyTo(id, uri)) "Berkas disimpan" else "Gagal menyimpan berkas") }
    }
}

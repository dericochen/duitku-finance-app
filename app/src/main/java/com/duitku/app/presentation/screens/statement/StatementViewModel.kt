package com.duitku.app.presentation.screens.statement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.LiveMonth
import com.duitku.app.domain.model.ReopenResult
import com.duitku.app.domain.model.StatementStatus
import com.duitku.app.domain.model.StatementSummary
import com.duitku.app.domain.model.VerifyResult
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatementUiState(
    val isLoading: Boolean = true,
    val periodYm: Int = 0,
    val currentPeriodYm: Int = 0,
    val month: LiveMonth? = null,
    val wallets: List<Wallet> = emptyList(),
    val selectedWalletId: Long? = null,
    val archive: StatementSummary? = null,
    val currencyCode: String = "IDR",
    val busy: Boolean = false,
) {
    val canGoNext: Boolean get() = periodYm != 0 && periodYm < currentPeriodYm
    val isClosed: Boolean get() = archive?.status == StatementStatus.CLOSED
    val isReopened: Boolean get() = archive?.status == StatementStatus.OPEN
    val periodLabel: String get() = if (periodYm == 0) "" else DateFormats.monthYear(periodYm.toYearMonth())
    val statusLabel: String get() = when {
        isClosed -> "Tertutup"
        isReopened -> "Berjalan (dibuka kembali)"
        else -> "Berjalan"
    }
}

sealed interface StatementEvent {
    data class Message(val text: String) : StatementEvent
    data class VerifyDone(val valid: Boolean, val message: String) : StatementEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statements: StatementRepository,
    walletRepository: WalletRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val argPeriod = savedStateHandle.get<Int>(Screen.StatementDetail.ARG_PERIOD)?.takeIf { it > 0 }

    private val period = MutableStateFlow(0)
    private val walletFilter = MutableStateFlow<Long?>(null)
    private val currentPeriod = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)

    private val _events = Channel<StatementEvent>(Channel.BUFFERED)
    val events: Flow<StatementEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val day = settingsRepository.current().monthStartDay
            val now = PeriodCalculator.periodOf(LocalDate.now(), day).toKey()
            currentPeriod.value = now
            period.value = argPeriod ?: now
        }
    }

    private val monthFlow: Flow<LiveMonth?> = combine(period, walletFilter) { p, w -> p to w }
        .flatMapLatest { (p, w) -> if (p == 0) flowOf(null) else statements.observeMonth(p, w) }

    private val archiveFlow: Flow<StatementSummary?> = period.flatMapLatest { p ->
        if (p == 0) flowOf(null) else statements.observeActive(p)
    }

    val uiState: StateFlow<StatementUiState> = combine(
        listOf(monthFlow, archiveFlow, walletRepository.observeWallets(), walletFilter, period, currentPeriod, busy, settingsRepository.settings),
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        StatementUiState(
            isLoading = (v[4] as Int) == 0 || v[0] == null,
            periodYm = v[4] as Int,
            currentPeriodYm = v[5] as Int,
            month = v[0] as LiveMonth?,
            archive = v[1] as StatementSummary?,
            wallets = (v[2] as List<Wallet>).filter { !it.isArchived },
            selectedWalletId = v[3] as Long?,
            busy = v[6] as Boolean,
            currencyCode = (v[7] as AppSettings).currencyCode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatementUiState())

    fun previous() = period.update { if (it == 0) it else it.toYearMonth().minusMonths(1).toKey() }

    fun next() = period.update {
        if (it == 0 || it >= currentPeriod.value) it else it.toYearMonth().plusMonths(1).toKey()
    }

    fun selectWallet(id: Long?) {
        walletFilter.value = id
    }

    fun closeMonth() = runBusy {
        val message = when (val result = statements.close(period.value)) {
            is CloseResult.Closed -> "Bulan ditutup: ${result.summary.documentNumber}"
            CloseResult.AlreadyClosed -> "Bulan ini sudah ditutup"
            CloseResult.NoData -> "Tidak ada transaksi pada bulan ini"
            is CloseResult.Failed -> result.message
        }
        _events.send(StatementEvent.Message(message))
    }

    fun reopenMonth(reason: String) = runBusy {
        val message = when (val result = statements.reopen(period.value, reason)) {
            is ReopenResult.Reopened -> if (result.laterClosedPeriods.isEmpty()) {
                "Bulan dibuka kembali. Tutup lagi setelah selesai mengedit."
            } else {
                "Bulan dibuka kembali. Bulan setelahnya yang sudah ditutup mungkin perlu ditutup ulang."
            }
            ReopenResult.NotClosed -> "Bulan ini tidak dalam keadaan tertutup"
            is ReopenResult.Failed -> result.message
        }
        _events.send(StatementEvent.Message(message))
    }

    fun verify() {
        val id = uiState.value.archive?.id ?: return
        viewModelScope.launch {
            when (val result = statements.verify(id)) {
                is VerifyResult.Valid -> _events.send(StatementEvent.VerifyDone(true, "Data utuh. SHA-256: ${result.checksum.take(16)}..."))
                is VerifyResult.Tampered -> _events.send(StatementEvent.VerifyDone(false, "Checksum tidak cocok! Data arsip mungkin berubah."))
                VerifyResult.NotFound -> _events.send(StatementEvent.Message("Arsip tidak ditemukan"))
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }
}

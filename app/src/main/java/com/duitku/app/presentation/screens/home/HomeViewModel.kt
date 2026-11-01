package com.duitku.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.startOfDayMillis
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.LiveMonth
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionItem
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.WalletWithBalance
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.domain.usecase.transaction.ObserveTransactionItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BudgetSummary(val limit: Long, val spent: Long) {
    val remaining: Long get() = limit - spent
    val percent: Int get() = if (limit <= 0) (if (spent > 0) 100 else 0) else ((spent * 100) / limit).coerceAtMost(999).toInt()
}

data class DailyExpense(val label: String, val amount: Long)

data class HomeUiState(
    val isLoading: Boolean = true,
    val ownerName: String = "",
    val currencyCode: String = "IDR",
    val totalBalance: Long = 0,
    val periodLabel: String = "",
    val income: Long = 0,
    val expense: Long = 0,
    val budget: BudgetSummary? = null,
    val last7Days: List<DailyExpense> = emptyList(),
    val recent: List<TransactionItem> = emptyList(),
    val monthsStored: Int = 0,
    val closedCount: Int = 0,
    val lastBackupAt: Long = 0,
    val backupConfigured: Boolean = false,
    val integrityOk: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    walletRepository: WalletRepository,
    statements: StatementRepository,
    budgets: BudgetRepository,
    transactions: TransactionRepository,
    observeItems: ObserveTransactionItemsUseCase,
) : ViewModel() {

    private val startDay = settingsRepository.settings.map { it.monthStartDay }.distinctUntilChanged()

    private val periodState = startDay.map { PeriodCalculator.periodOf(LocalDate.now(), it) }

    private val monthFlow = periodState.flatMapLatest { statements.observeMonth(it.toKey(), null) }

    private val budgetFlow = periodState.flatMapLatest { budgets.observeLines(it.toKey()) }.map { lines ->
        val total = lines.firstOrNull { it.isTotal }
        when {
            total != null -> BudgetSummary(total.limit, total.spent)
            lines.isNotEmpty() -> BudgetSummary(lines.sumOf { it.limit }, lines.sumOf { it.spent })
            else -> null
        }
    }

    private val last7 = transactions.observeFiltered(
        TransactionFilter(
            fromMillis = LocalDate.now().minusDays(6).startOfDayMillis(),
            types = setOf(TransactionType.EXPENSE),
        ),
    ).map { list ->
        val byDay = list.groupBy { it.occurredAt.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amount } }
        (6 downTo 0).map { back ->
            val d = LocalDate.now().minusDays(back.toLong())
            DailyExpense(DateFormats.dayName(d), byDay[d] ?: 0L)
        }
    }

    private val dataInfo = combine(
        transactions.observeOldestAt(), statements.observeClosedCount(), startDay,
    ) { oldest, closed, day ->
        val months = if (oldest == null) {
            0
        } else {
            val first = PeriodCalculator.periodOfMillis(oldest, day)
            val last = PeriodCalculator.periodOf(LocalDate.now(), day)
            PeriodCalculator.monthsBetween(first, last).size
        }
        months to closed
    }

    private data class Head(
        val settings: AppSettings,
        val wallets: List<WalletWithBalance>,
        val month: LiveMonth,
        val budget: BudgetSummary?,
    )

    private data class Tail(
        val last7: List<DailyExpense>,
        val recent: List<TransactionItem>,
        val info: Pair<Int, Int>,
    )

    private val head = combine(
        settingsRepository.settings, walletRepository.observeWalletsWithBalance(), monthFlow, budgetFlow,
    ) { settings, wallets, month, budget -> Head(settings, wallets, month, budget) }

    private val tail = combine(last7, observeItems.recent(5), dataInfo) { days, recent, info -> Tail(days, recent, info) }

    val uiState: StateFlow<HomeUiState> = combine(head, tail) { h, t ->
        HomeUiState(
            isLoading = false,
            ownerName = h.settings.ownerName,
            currencyCode = h.settings.currencyCode,
            totalBalance = h.wallets.sumOf { it.balance },
            periodLabel = DateFormats.monthYear(YearMonth.of(h.month.periodYm / 100, h.month.periodYm % 100)),
            income = h.month.summary.income,
            expense = h.month.summary.expense,
            budget = h.budget,
            last7Days = t.last7,
            recent = t.recent,
            monthsStored = t.info.first,
            closedCount = t.info.second,
            lastBackupAt = h.settings.lastBackupAt,
            backupConfigured = h.settings.backupFolderUri.isNotBlank(),
            integrityOk = h.settings.lastIntegrityOk,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
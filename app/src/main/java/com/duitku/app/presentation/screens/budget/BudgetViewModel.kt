package com.duitku.app.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.budget.SaveBudgetUseCase
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Satu baris anggaran beserta info kategorinya. */
data class BudgetItem(
    val line: BudgetLine,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
)

/** Keadaan dialog tambah/ubah anggaran. [categoryId] null = anggaran total bulanan. */
data class BudgetEditState(
    val existingId: Long = 0,
    val categoryId: Long? = null,
    val amountInput: String = "",
    val rollover: Boolean = false,
    val error: String? = null,
    val categoryLocked: Boolean = false,
)

data class BudgetUiState(
    val isLoading: Boolean = true,
    val periodYm: Int = 0,
    val currentPeriodYm: Int = 0,
    val items: List<BudgetItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyCode: String = "IDR",
    val edit: BudgetEditState? = null,
) {
    val periodLabel: String get() = if (periodYm == 0) "" else DateFormats.monthYear(periodYm.toYearMonth())
    val canGoNext: Boolean get() = periodYm in 1 until currentPeriodYm
    val total: BudgetItem? get() = items.firstOrNull { it.line.isTotal }
    val byCategory: List<BudgetItem> get() = items.filter { !it.line.isTotal }

    /** Kategori pengeluaran yang belum punya anggaran di periode ini. */
    val unbudgetedCategories: List<Category>
        get() = categories.filter { c -> items.none { it.line.budget.categoryId == c.id } }
    val hasTotalBudget: Boolean get() = total != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val saveBudget: SaveBudgetUseCase,
) : ViewModel() {

    private val period = MutableStateFlow(0)
    private val currentPeriod = MutableStateFlow(0)
    private val edit = MutableStateFlow<BudgetEditState?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val now = PeriodCalculator.periodOf(LocalDate.now(), settingsRepository.current().monthStartDay).toKey()
            currentPeriod.value = now
            period.value = now
        }
    }

    private val lines = period.flatMapLatest { if (it == 0) flowOf(emptyList()) else budgets.observeLines(it) }

    val uiState: StateFlow<BudgetUiState> = combine(
        lines, categoryRepository.observeAll(), period, currentPeriod, edit,
    ) { lines, all, p, current, editing ->
        val expenseParents = all.filter { it.kind == CategoryKind.EXPENSE && it.parentId == null && !it.isArchived }
        val byId = all.associateBy { it.id }
        BudgetUiState(
            isLoading = p == 0,
            periodYm = p,
            currentPeriodYm = current,
            items = lines.map { line ->
                val cat = line.budget.categoryId?.let(byId::get)
                BudgetItem(
                    line = line,
                    name = if (line.isTotal) "Total bulanan" else cat?.name ?: "Kategori dihapus",
                    iconKey = if (line.isTotal) "wallet" else cat?.iconKey ?: "other",
                    colorArgb = cat?.colorArgb ?: 0xFF0B6B4F.toInt(),
                )
            },
            categories = expenseParents,
            edit = editing,
        )
    }.combine(settingsRepository.settings) { state, settings -> state.copy(currencyCode = settings.currencyCode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun previous() = period.update { if (it == 0) it else it.toYearMonth().minusMonths(1).toKey() }
    fun next() = period.update { if (it == 0 || it >= currentPeriod.value) it else it.toYearMonth().plusMonths(1).toKey() }

    fun startAdd() {
        edit.value = BudgetEditState(categoryId = if (uiState.value.hasTotalBudget) uiState.value.unbudgetedCategories.firstOrNull()?.id else null)
    }

    fun startEdit(item: BudgetItem) {
        edit.value = BudgetEditState(
            existingId = item.line.budget.id,
            categoryId = item.line.budget.categoryId,
            amountInput = item.line.budget.amount.toString(),
            rollover = item.line.budget.rollover,
            categoryLocked = true,
        )
    }

    fun dismissEdit() {
        edit.value = null
    }

    fun onCategoryChange(id: Long?) = edit.update { it?.copy(categoryId = id, error = null) }
    fun onAmountChange(value: String) = edit.update { it?.copy(amountInput = value.filter(Char::isDigit).trimStart('0').take(12), error = null) }
    fun onRolloverChange(value: Boolean) = edit.update { it?.copy(rollover = value) }

    fun save() {
        val e = edit.value ?: return
        val amount = MoneyFormatter.parse(e.amountInput)
        if (amount == null) {
            edit.update { it?.copy(error = "Nominal wajib diisi") }
            return
        }
        viewModelScope.launch {
            val result = saveBudget(Budget(id = e.existingId, periodYm = period.value, categoryId = e.categoryId, amount = amount, rollover = e.rollover))
            when (result) {
                is UseCaseResult.Success -> {
                    edit.value = null
                    _messages.send("Anggaran disimpan")
                }
                is UseCaseResult.Failure -> edit.update { it?.copy(error = result.message) }
            }
        }
    }

    fun delete() {
        val id = edit.value?.existingId?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            budgets.delete(id)
            edit.value = null
            _messages.send("Anggaran dihapus")
        }
    }

    fun copyFromPrevious() {
        viewModelScope.launch {
            val count = budgets.copyFromPrevious(period.value)
            _messages.send(if (count > 0) "$count anggaran disalin dari bulan sebelumnya" else "Tidak ada anggaran baru untuk disalin")
        }
    }
}

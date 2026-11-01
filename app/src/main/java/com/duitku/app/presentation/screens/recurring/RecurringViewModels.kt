package com.duitku.app.presentation.screens.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.RecurringTransaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.RecurringRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.domain.usecase.planning.RecurringMath
import com.duitku.app.domain.usecase.planning.SaveRecurringUseCase
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.AmountInput
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class RecurringItem(
    val rule: RecurringTransaction,
    val walletName: String,
    val categoryName: String?,
)

data class RecurringListUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringItem> = emptyList(),
    val currencyCode: String = "IDR",
    /** Total beban bulanan perkiraan dari semua aturan pengeluaran aktif. */
    val monthlyBurden: Long = 0,
)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    private val repository: RecurringRepository,
    walletRepository: WalletRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<RecurringListUiState> = combine(
        repository.observeAll(), walletRepository.observeWallets(), categoryRepository.observeAll(), settingsRepository.settings,
    ) { rules, wallets, cats, settings ->
        val w = wallets.associateBy { it.id }
        val c = cats.associateBy { it.id }
        RecurringListUiState(
            isLoading = false,
            items = rules.map { RecurringItem(it, w[it.walletId]?.name ?: "-", it.categoryId?.let { id -> c[id]?.name }) },
            currencyCode = settings.currencyCode,
            monthlyBurden = rules.filter { it.isActive && it.type == TransactionType.EXPENSE }.sumOf { RecurringMath.monthlyEquivalent(it.amount, it.frequency, it.intervalCount) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())

    fun toggle(rule: RecurringTransaction) {
        viewModelScope.launch { repository.setActive(rule.id, !rule.isActive) }
    }

    fun delete(rule: RecurringTransaction) {
        viewModelScope.launch { repository.delete(rule.id) }
    }
}

data class RecurringEditUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val title: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalInput: String = "1",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val wallets: List<Wallet> = emptyList(),
    val categoryTree: List<CategoryNode> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecurringRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val saveRecurring: SaveRecurringUseCase,
) : ViewModel() {

    private val ruleId = savedStateHandle.get<Long>(Screen.RecurringEdit.ARG_ID)?.takeIf { it > 0 } ?: 0L

    private val _state = MutableStateFlow(RecurringEditUiState(isEdit = ruleId != 0L))
    val state: StateFlow<RecurringEditUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var categories: List<Category> = emptyList()
    private var original: RecurringTransaction? = null

    init {
        viewModelScope.launch {
            val wallets = walletRepository.observeWallets().first().filter { !it.isArchived }
            categories = categoryRepository.observeAll().first()
            val rule = if (ruleId != 0L) repository.get(ruleId) else null
            if (ruleId != 0L && rule == null) {
                _events.send(UiEvent.NavigateBack)
                return@launch
            }
            original = rule
            val type = rule?.type ?: TransactionType.EXPENSE
            _state.update {
                it.copy(
                    isLoading = false, wallets = wallets, categoryTree = treeFor(type),
                    walletId = rule?.walletId ?: wallets.firstOrNull()?.id,
                    title = rule?.title.orEmpty(), type = type,
                    amountInput = rule?.let { r -> AmountInput.fromLong(r.amount) }.orEmpty(),
                    toWalletId = rule?.toWalletId, categoryId = rule?.categoryId, subCategoryId = rule?.subCategoryId,
                    note = rule?.note.orEmpty(), frequency = rule?.frequency ?: RecurrenceFrequency.MONTHLY,
                    intervalInput = (rule?.intervalCount ?: 1).toString(),
                    startDate = rule?.startAt?.toLocalDate() ?: LocalDate.now(), endDate = rule?.endAt?.toLocalDate(),
                )
            }
        }
    }

    private fun treeFor(type: TransactionType): List<CategoryNode> {
        val kind = when (type) {
            TransactionType.INCOME -> CategoryKind.INCOME
            TransactionType.EXPENSE -> CategoryKind.EXPENSE
            TransactionType.TRANSFER -> return emptyList()
        }
        val active = categories.filter { it.kind == kind && !it.isArchived }
        val children = active.filter { it.parentId != null }.groupBy { it.parentId }
        return active.filter { it.parentId == null }.map { CategoryNode(it, children[it.id].orEmpty()) }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v.take(45), error = null) }
    fun onType(t: TransactionType) = _state.update {
        it.copy(type = t, categoryId = null, subCategoryId = null, categoryTree = treeFor(t), toWalletId = if (t == TransactionType.TRANSFER) it.toWalletId else null, error = null)
    }
    fun onAmount(v: String) = _state.update { it.copy(amountInput = v.filter(Char::isDigit).trimStart('0').take(AmountInput.MAX_DIGITS), error = null) }
    fun onWallet(id: Long) = _state.update { it.copy(walletId = id, toWalletId = if (it.toWalletId == id) null else it.toWalletId, error = null) }
    fun onToWallet(id: Long) = _state.update { it.copy(toWalletId = id, error = null) }
    fun onCategory(id: Long) = _state.update { it.copy(categoryId = id, subCategoryId = null, error = null) }
    fun onSubCategory(id: Long?) = _state.update { it.copy(subCategoryId = id) }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(200)) }
    fun onFrequency(f: RecurrenceFrequency) = _state.update { it.copy(frequency = f) }
    fun onInterval(v: String) = _state.update { it.copy(intervalInput = v.filter(Char::isDigit).take(2), error = null) }
    fun onStartDate(d: LocalDate) = _state.update { it.copy(startDate = d, error = null) }
    fun onEndDate(d: LocalDate?) = _state.update { it.copy(endDate = d, error = null) }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val rule = RecurringTransaction(
            id = ruleId, title = s.title, type = s.type, amount = MoneyFormatter.parse(s.amountInput) ?: 0L,
            walletId = s.walletId ?: 0L, toWalletId = s.toWalletId, categoryId = s.categoryId, subCategoryId = s.subCategoryId,
            note = s.note, frequency = s.frequency, intervalCount = s.intervalInput.toIntOrNull() ?: 0,
            // Jam eksekusi = 08:00 pada tanggal mulai.
            startAt = s.startDate.atTime(LocalTime.of(8, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endAt = s.endDate?.endOfDayMillis(), nextDueAt = original?.nextDueAt ?: 0L,
            lastRunAt = original?.lastRunAt, isActive = original?.isActive ?: true,
        )
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = saveRecurring(rule)) {
                is UseCaseResult.Success -> {
                    _events.send(UiEvent.ShowMessage("Transaksi berulang disimpan"))
                    _events.send(UiEvent.NavigateBack)
                }
                is UseCaseResult.Failure -> _state.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }
}

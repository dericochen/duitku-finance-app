package com.duitku.app.presentation.screens.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.core.util.toLocalDateTime
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionError
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.domain.usecase.transaction.DeleteTransactionResult
import com.duitku.app.domain.usecase.transaction.DeleteTransactionUseCase
import com.duitku.app.domain.usecase.transaction.SaveTransactionResult
import com.duitku.app.domain.usecase.transaction.SaveTransactionUseCase
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.AmountInput
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionEditUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val currencyCode: String = "IDR",

    // Data pilihan
    val wallets: List<Wallet> = emptyList(),
    val categoryTree: List<CategoryNode> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),

    // Error per kolom
    val amountError: String? = null,
    val walletError: String? = null,
    val toWalletError: String? = null,
    val categoryError: String? = null,
    val noteError: String? = null,
    val tagError: String? = null,
    val dateError: String? = null,

    val isSaving: Boolean = false,
    /** Periode (YYYYMM) yang menolak perubahan karena sudah ditutup. */
    val closedPeriodYm: Int? = null,
) {
    val amount: Long get() = MoneyFormatter.parse(amountInput) ?: 0L
    val selectedCategory: CategoryNode? get() = categoryTree.firstOrNull { it.category.id == categoryId }
}

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val saveTransaction: SaveTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {

    private val transactionId: Long =
        savedStateHandle.get<Long>(Screen.TransactionEdit.ARG_ID)?.takeIf { it > 0 } ?: 0L
    private val initialType: TransactionType =
        savedStateHandle.get<String>(Screen.TransactionEdit.ARG_TYPE)
            ?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
            ?: TransactionType.EXPENSE

    private val _state = MutableStateFlow(
        TransactionEditUiState(isEdit = transactionId != 0L, type = initialType),
    )
    val state: StateFlow<TransactionEditUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var original: Transaction? = null
    private var allCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = settingsRepository.current()
        val wallets = walletRepository.observeWallets().first().filter { !it.isArchived }
        allCategories = categoryRepository.observeAll().first()
        val tags = transactionRepository.allTags()

        val existing = if (transactionId != 0L) transactionRepository.get(transactionId) else null
        if (transactionId != 0L && existing == null) {
            _events.send(UiEvent.ShowMessage("Transaksi tidak ditemukan"))
            _events.send(UiEvent.NavigateBack)
            return
        }
        original = existing

        val type = existing?.type ?: initialType
        _state.update { s ->
            s.copy(
                isLoading = false,
                type = type,
                currencyCode = settings.currencyCode,
                wallets = wallets,
                tagSuggestions = tags,
                amountInput = existing?.let { AmountInput.fromLong(it.amount) } ?: "",
                walletId = existing?.walletId ?: wallets.firstOrNull()?.id,
                toWalletId = existing?.toWalletId,
                categoryId = existing?.categoryId,
                subCategoryId = existing?.subCategoryId,
                note = existing?.note.orEmpty(),
                tags = existing?.tags.orEmpty(),
                dateTime = existing?.occurredAt?.toLocalDateTime() ?: LocalDateTime.now().withSecond(0).withNano(0),
                categoryTree = treeFor(type),
            )
        }
    }

    private fun treeFor(type: TransactionType): List<CategoryNode> {
        val kind = when (type) {
            TransactionType.INCOME -> CategoryKind.INCOME
            TransactionType.EXPENSE -> CategoryKind.EXPENSE
            TransactionType.TRANSFER -> return emptyList()
        }
        val active = allCategories.filter { it.kind == kind && !it.isArchived }
        val children = active.filter { it.parentId != null }.groupBy { it.parentId }
        return active.filter { it.parentId == null }.map { CategoryNode(it, children[it.id].orEmpty()) }
    }

    // ---------- Input ----------
    fun onTypeChange(type: TransactionType) = _state.update { s ->
        if (s.type == type) return@update s
        s.copy(
            type = type,
            categoryId = null,
            subCategoryId = null,
            categoryTree = treeFor(type),
            categoryError = null,
            toWalletError = null,
            toWalletId = if (type == TransactionType.TRANSFER) s.toWalletId else null,
        )
    }

    fun onKey(key: String) = _state.update { it.copy(amountInput = AmountInput.append(it.amountInput, key), amountError = null) }
    fun onBackspace() = _state.update { it.copy(amountInput = AmountInput.backspace(it.amountInput), amountError = null) }
    fun onClearAmount() = _state.update { it.copy(amountInput = "", amountError = null) }

    fun onWalletSelected(id: Long) = _state.update {
        it.copy(walletId = id, walletError = null, toWalletId = if (it.toWalletId == id) null else it.toWalletId)
    }

    fun onToWalletSelected(id: Long) = _state.update { it.copy(toWalletId = id, toWalletError = null) }

    fun onCategorySelected(id: Long) = _state.update { it.copy(categoryId = id, subCategoryId = null, categoryError = null) }
    fun onSubCategorySelected(id: Long?) = _state.update { it.copy(subCategoryId = id) }

    fun onNoteChange(value: String) = _state.update {
        it.copy(note = value.take(TransactionRules.MAX_NOTE_LENGTH + 20), noteError = null)
    }

    fun onTagInputChange(value: String) = _state.update { it.copy(tagInput = value.take(TransactionRules.MAX_TAG_LENGTH + 5), tagError = null) }

    fun addTag(raw: String = _state.value.tagInput) {
        val tag = TransactionRules.normalizeTag(raw)
        if (tag.isEmpty()) {
            _state.update { it.copy(tagInput = "") }
            return
        }
        _state.update { s ->
            when {
                tag.length > TransactionRules.MAX_TAG_LENGTH ->
                    s.copy(tagError = TransactionError.TAG_TOO_LONG.message)
                s.tags.size >= TransactionRules.MAX_TAGS ->
                    s.copy(tagError = TransactionError.TOO_MANY_TAGS.message)
                else -> s.copy(tags = (s.tags + tag).distinct(), tagInput = "", tagError = null)
            }
        }
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(dateTime = it.dateTime.with(date), dateError = null) }
    fun onTimeChange(time: LocalTime) = _state.update { it.copy(dateTime = it.dateTime.with(time)) }

    fun dismissClosedPeriod() = _state.update { it.copy(closedPeriodYm = null) }

    // ---------- Aksi ----------
    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val tx = Transaction(
            id = transactionId,
            type = s.type,
            amount = s.amount,
            walletId = s.walletId ?: 0,
            toWalletId = s.toWalletId,
            categoryId = s.categoryId,
            subCategoryId = s.subCategoryId,
            note = s.note,
            tags = s.tags,
            occurredAt = s.dateTime.toEpochMillis(),
            recurringId = original?.recurringId,
            createdAt = original?.createdAt ?: 0,
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = saveTransaction(tx, amountEntered = s.amountInput.isNotEmpty())) {
                is SaveTransactionResult.Success -> {
                    _events.send(UiEvent.ShowMessage(if (s.isEdit) "Transaksi diperbarui" else "Transaksi disimpan"))
                    _events.send(UiEvent.NavigateBack)
                }
                is SaveTransactionResult.Invalid -> _state.update { applyErrors(it, result.errors).copy(isSaving = false) }
                is SaveTransactionResult.PeriodClosed -> _state.update { it.copy(isSaving = false, closedPeriodYm = result.periodYm) }
                is SaveTransactionResult.Failed -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(UiEvent.ShowMessage(result.message))
                }
            }
        }
    }

    fun delete() {
        if (transactionId == 0L) return
        viewModelScope.launch {
            when (val result = deleteTransaction(transactionId)) {
                is DeleteTransactionResult.Deleted -> {
                    _events.send(UiEvent.ShowMessage("Transaksi dihapus"))
                    _events.send(UiEvent.NavigateBack)
                }
                is DeleteTransactionResult.PeriodClosed -> _state.update { it.copy(closedPeriodYm = result.periodYm) }
                DeleteTransactionResult.NotFound -> _events.send(UiEvent.NavigateBack)
                is DeleteTransactionResult.Failed -> _events.send(UiEvent.ShowMessage(result.message))
            }
        }
    }

    private fun applyErrors(s: TransactionEditUiState, errors: List<TransactionError>): TransactionEditUiState {
        fun msg(vararg match: TransactionError) = errors.firstOrNull { it in match }?.message
        return s.copy(
            amountError = msg(TransactionError.AMOUNT_EMPTY, TransactionError.AMOUNT_NOT_POSITIVE, TransactionError.AMOUNT_TOO_LARGE),
            walletError = msg(TransactionError.WALLET_MISSING),
            toWalletError = msg(TransactionError.TO_WALLET_MISSING, TransactionError.SAME_WALLET),
            categoryError = msg(TransactionError.CATEGORY_MISSING),
            noteError = msg(TransactionError.NOTE_TOO_LONG),
            tagError = msg(TransactionError.TOO_MANY_TAGS, TransactionError.TAG_TOO_LONG),
            dateError = msg(TransactionError.DATE_OUT_OF_RANGE),
        )
    }
}

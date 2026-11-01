package com.duitku.app.presentation.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionItem
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.domain.usecase.transaction.DeleteTransactionResult
import com.duitku.app.domain.usecase.transaction.DeleteTransactionUseCase
import com.duitku.app.domain.usecase.transaction.ObserveTransactionItemsUseCase
import com.duitku.app.domain.usecase.transaction.UndoDeleteTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionSearchUiState(
    val isLoading: Boolean = true,
    val filter: TransactionFilter = TransactionFilter(),
    val items: List<TransactionItem> = emptyList(),
    val currencyCode: String = "IDR",
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    val totalIncome: Long get() = items.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount }
    val totalExpense: Long get() = items.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount }
}

sealed interface TransactionSearchEvent {
    data class Message(val text: String) : TransactionSearchEvent

    /** Transaksi baru dihapus; UI menawarkan Urungkan. */
    data class Deleted(val transaction: Transaction) : TransactionSearchEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionSearchViewModel @Inject constructor(
    observeItems: ObserveTransactionItemsUseCase,
    walletRepository: WalletRepository,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val undoDelete: UndoDeleteTransactionUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter())
    private val tags = MutableStateFlow<List<String>>(emptyList())

    private val _events = Channel<TransactionSearchEvent>(Channel.BUFFERED)
    val events: Flow<TransactionSearchEvent> = _events.receiveAsFlow()

    // Kata kunci di-debounce supaya query tidak dijalankan di setiap ketukan; filter lain langsung berlaku.
    private val effectiveFilter: Flow<TransactionFilter> = filter
        .debounce { current -> if (current.query.isNotBlank()) 250L else 0L }
        .distinctUntilChanged()

    val uiState: StateFlow<TransactionSearchUiState> = combine(
        effectiveFilter,
        effectiveFilter.flatMapLatest { observeItems(it) },
        walletRepository.observeWallets(),
        categoryRepository.observeAll(),
        settingsRepository.settings.map { it.currencyCode },
    ) { f, items, wallets, categories, currency ->
        TransactionSearchUiState(
            isLoading = false,
            filter = f,
            items = items,
            currencyCode = currency,
            wallets = wallets,
            categories = categories,
        )
    }.combine(tags) { state, tagList -> state.copy(tags = tagList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionSearchUiState())

    /** Nilai kotak pencarian langsung (tanpa debounce) agar pengetikan terasa responsif. */
    val queryText: StateFlow<String> = filter.map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch { tags.value = transactionRepository.allTags() }
    }

    fun onQueryChange(query: String) = filter.update { it.copy(query = query.take(100)) }

    fun applyFilter(newFilter: TransactionFilter) = filter.update { newFilter.copy(query = it.query) }

    fun resetFilter() = filter.update { TransactionFilter(query = it.query) }

    fun delete(id: Long) {
        viewModelScope.launch {
            when (val result = deleteTransaction(id)) {
                is DeleteTransactionResult.Deleted -> _events.send(TransactionSearchEvent.Deleted(result.transaction))
                is DeleteTransactionResult.PeriodClosed ->
                    _events.send(TransactionSearchEvent.Message("Bulan transaksi ini sudah ditutup. Buka kembali bulannya dari Arsip Mutasi untuk menghapus."))
                DeleteTransactionResult.NotFound -> Unit
                is DeleteTransactionResult.Failed -> _events.send(TransactionSearchEvent.Message(result.message))
            }
        }
    }

    fun undo(transaction: Transaction) {
        viewModelScope.launch {
            if (!undoDelete(transaction)) {
                _events.send(TransactionSearchEvent.Message("Gagal mengembalikan transaksi"))
            }
        }
    }
}

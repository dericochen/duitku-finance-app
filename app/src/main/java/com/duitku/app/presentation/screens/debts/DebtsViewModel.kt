package com.duitku.app.presentation.screens.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.model.DebtPayment
import com.duitku.app.domain.model.DebtState
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.DebtRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.planning.AddDebtPaymentUseCase
import com.duitku.app.domain.usecase.planning.DebtCalculator
import com.duitku.app.domain.usecase.planning.SaveDebtUseCase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebtItem(val item: DebtWithPaid, val state: DebtState)

data class DebtFormState(
    val id: Long = 0,
    val kind: DebtKind = DebtKind.DEBT,
    val party: String = "",
    val amountInput: String = "",
    val due: LocalDate? = null,
    val note: String = "",
    val reminder: Boolean = true,
    val error: String? = null,
)

data class PaymentFormState(
    val debt: DebtWithPaid,
    val amountInput: String = "",
    val note: String = "",
    val error: String? = null,
)

data class DebtsUiState(
    val isLoading: Boolean = true,
    val kind: DebtKind = DebtKind.DEBT,
    val items: List<DebtItem> = emptyList(),
    val totalRemaining: Long = 0,
    val currencyCode: String = "IDR",
    val form: DebtFormState? = null,
    val payment: PaymentFormState? = null,
    val detail: DebtWithPaid? = null,
    val detailPayments: List<DebtPayment> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val repository: DebtRepository,
    settingsRepository: SettingsRepository,
    private val saveDebt: SaveDebtUseCase,
    private val addPayment: AddDebtPaymentUseCase,
) : ViewModel() {

    private val kind = MutableStateFlow(DebtKind.DEBT)
    private val form = MutableStateFlow<DebtFormState?>(null)
    private val payment = MutableStateFlow<PaymentFormState?>(null)
    private val detailId = MutableStateFlow<Long?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val detail: Flow<Pair<DebtWithPaid?, List<DebtPayment>>> = detailId.flatMapLatest { id ->
        if (id == null) {
            flowOf<Pair<DebtWithPaid?, List<DebtPayment>>>(null to emptyList())
        } else {
            combine(
                repository.observeAll().map { list -> list.firstOrNull { it.debt.id == id } },
                repository.observePayments(id),
            ) { d, p -> d to p }
        }
    }

    private data class Head(val kind: DebtKind, val all: List<DebtWithPaid>, val currency: String)

    private val head = combine(kind, repository.observeAll(), settingsRepository.settings) { k, all, s -> Head(k, all, s.currencyCode) }
    private val dialogs = combine(form, payment, detail) { f, p, d -> Triple(f, p, d) }

    val uiState: StateFlow<DebtsUiState> = combine(head, dialogs) { h, d ->
        val today = LocalDate.now()
        val ofKind = h.all.filter { it.debt.kind == h.kind }
        DebtsUiState(
            isLoading = false, kind = h.kind, currencyCode = h.currency,
            items = ofKind.map { DebtItem(it, DebtCalculator.state(it, today)) },
            totalRemaining = ofKind.filter { !it.debt.isPaidOff }.sumOf { it.remaining },
            form = d.first, payment = d.second, detail = d.third.first, detailPayments = d.third.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState())

    fun selectKind(k: DebtKind) { kind.value = k }

    // ----- form -----
    fun startAdd() { form.value = DebtFormState(kind = kind.value) }

    fun startEdit(d: Debt) {
        form.value = DebtFormState(d.id, d.kind, d.partyName, d.principalAmount.toString(), d.dueDate?.toLocalDate(), d.note, d.reminderEnabled)
    }

    fun dismissForm() { form.value = null }
    fun onParty(v: String) = form.update { it?.copy(party = v.take(45), error = null) }
    fun onAmount(v: String) = form.update { it?.copy(amountInput = v.filter(Char::isDigit).trimStart('0').take(12), error = null) }
    fun onDue(d: LocalDate?) = form.update { it?.copy(due = d) }
    fun onNote(v: String) = form.update { it?.copy(note = v.take(200)) }
    fun onReminder(v: Boolean) = form.update { it?.copy(reminder = v) }

    fun saveForm() {
        val f = form.value ?: return
        viewModelScope.launch {
            val debt = Debt(
                id = f.id, kind = f.kind, partyName = f.party, principalAmount = MoneyFormatter.parse(f.amountInput) ?: 0L,
                dueDate = f.due?.endOfDayMillis(), note = f.note, reminderEnabled = f.reminder,
            )
            when (val r = saveDebt(debt)) {
                is UseCaseResult.Success -> { form.value = null; _messages.send("Catatan disimpan") }
                is UseCaseResult.Failure -> form.update { it?.copy(error = r.message) }
            }
        }
    }

    fun delete(d: Debt) {
        viewModelScope.launch {
            repository.delete(d.id)
            detailId.value = null
            _messages.send("Catatan dihapus")
        }
    }

    fun markPaidOff(d: Debt) {
        viewModelScope.launch {
            repository.markPaidOff(d.id)
            _messages.send("Ditandai lunas")
        }
    }

    // ----- pembayaran -----
    fun startPayment(d: DebtWithPaid) { payment.value = PaymentFormState(d, amountInput = d.remaining.toString()) }
    fun dismissPayment() { payment.value = null }
    fun onPaymentAmount(v: String) = payment.update { it?.copy(amountInput = v.filter(Char::isDigit).trimStart('0').take(12), error = null) }
    fun onPaymentNote(v: String) = payment.update { it?.copy(note = v.take(100)) }

    fun savePayment() {
        val p = payment.value ?: return
        val amount = MoneyFormatter.parse(p.amountInput)
        if (amount == null) {
            payment.update { it?.copy(error = "Nominal wajib diisi") }
            return
        }
        viewModelScope.launch {
            when (val r = addPayment(p.debt, amount, p.note, System.currentTimeMillis())) {
                is UseCaseResult.Success -> {
                    payment.value = null
                    _messages.send(if (r.value.debt.isPaidOff) "Lunas" else "Pembayaran dicatat")
                }
                is UseCaseResult.Failure -> payment.update { it?.copy(error = r.message) }
            }
        }
    }

    fun openDetail(d: DebtWithPaid) { detailId.value = d.debt.id }
    fun closeDetail() { detailId.value = null }
}

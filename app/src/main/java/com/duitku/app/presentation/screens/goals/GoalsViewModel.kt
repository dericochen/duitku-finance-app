package com.duitku.app.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.GoalContribution
import com.duitku.app.domain.model.GoalEstimate
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.GoalRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.planning.AddGoalContributionUseCase
import com.duitku.app.domain.usecase.planning.GoalCalculator
import com.duitku.app.domain.usecase.planning.SaveGoalUseCase
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

data class GoalItem(val goal: SavingGoal, val estimate: GoalEstimate)

data class GoalFormState(
    val id: Long = 0,
    val name: String = "",
    val targetInput: String = "",
    val deadline: LocalDate? = null,
    val colorArgb: Int = Palette.default,
    val iconKey: String = "savings",
    val error: String? = null,
)

data class ContributionFormState(
    val goal: SavingGoal,
    val amountInput: String = "",
    val note: String = "",
    val withdraw: Boolean = false,
    val error: String? = null,
)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val items: List<GoalItem> = emptyList(),
    val currencyCode: String = "IDR",
    val form: GoalFormState? = null,
    val contribution: ContributionFormState? = null,
    val detail: SavingGoal? = null,
    val detailHistory: List<GoalContribution> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
    settingsRepository: SettingsRepository,
    private val saveGoal: SaveGoalUseCase,
    private val addContribution: AddGoalContributionUseCase,
) : ViewModel() {

    private val form = MutableStateFlow<GoalFormState?>(null)
    private val contribution = MutableStateFlow<ContributionFormState?>(null)
    private val detailId = MutableStateFlow<Long?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val items: Flow<List<GoalItem>> = repository.observeGoals().map { goals ->
        goals.map { g -> GoalItem(g, GoalCalculator.estimate(g, repository.getContributions(g.id))) }
    }

    private val detail: Flow<Pair<SavingGoal?, List<GoalContribution>>> = detailId.flatMapLatest { id ->
        if (id == null) {
            flowOf<Pair<SavingGoal?, List<GoalContribution>>>(null to emptyList())
        } else {
            combine(
                repository.observeGoals().map { goals -> goals.firstOrNull { it.id == id } },
                repository.observeContributions(id),
            ) { goal, history -> goal to history }
        }
    }
    val uiState: StateFlow<GoalsUiState> = combine(items, settingsRepository.settings, form, contribution, detail) { list, settings, f, c, d ->
        GoalsUiState(
            isLoading = false, items = list, currencyCode = settings.currencyCode, form = f, contribution = c,
            detail = d.first, detailHistory = d.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    // ----- form target -----
    fun startAdd() { form.value = GoalFormState() }

    fun startEdit(goal: SavingGoal) {
        form.value = GoalFormState(
            goal.id, goal.name, goal.targetAmount.toString(), goal.deadline?.toLocalDate(), goal.colorArgb, goal.iconKey,
        )
    }

    fun dismissForm() { form.value = null }
    fun onName(v: String) = form.update { it?.copy(name = v.take(45), error = null) }
    fun onTarget(v: String) = form.update { it?.copy(targetInput = v.filter(Char::isDigit).trimStart('0').take(12), error = null) }
    fun onDeadline(d: LocalDate?) = form.update { it?.copy(deadline = d) }
    fun onColor(c: Int) = form.update { it?.copy(colorArgb = c) }

    fun saveForm() {
        val f = form.value ?: return
        viewModelScope.launch {
            val goal = SavingGoal(
                id = f.id, name = f.name, targetAmount = MoneyFormatter.parse(f.targetInput) ?: 0L,
                deadline = f.deadline?.endOfDayMillis(), iconKey = f.iconKey, colorArgb = f.colorArgb,
            )
            when (val r = saveGoal(goal)) {
                is UseCaseResult.Success -> { form.value = null; _messages.send("Target disimpan") }
                is UseCaseResult.Failure -> form.update { it?.copy(error = r.message) }
            }
        }
    }

    fun delete(goal: SavingGoal) {
        viewModelScope.launch {
            repository.delete(goal.id)
            detailId.value = null
            form.value = null
            _messages.send("Target dihapus")
        }
    }

    // ----- setoran -----
    fun startContribution(goal: SavingGoal, withdraw: Boolean = false) { contribution.value = ContributionFormState(goal, withdraw = withdraw) }
    fun dismissContribution() { contribution.value = null }
    fun onAmount(v: String) = contribution.update { it?.copy(amountInput = v.filter(Char::isDigit).trimStart('0').take(12), error = null) }
    fun onNote(v: String) = contribution.update { it?.copy(note = v.take(100)) }
    fun onWithdraw(w: Boolean) = contribution.update { it?.copy(withdraw = w, error = null) }

    fun saveContribution() {
        val c = contribution.value ?: return
        val amount = MoneyFormatter.parse(c.amountInput)
        if (amount == null) {
            contribution.update { it?.copy(error = "Nominal wajib diisi") }
            return
        }
        viewModelScope.launch {
            val signed = if (c.withdraw) -amount else amount
            when (val r = addContribution(c.goal, signed, c.note, System.currentTimeMillis())) {
                is UseCaseResult.Success -> {
                    contribution.value = null
                    _messages.send(if (r.value.isCompleted && !c.goal.isCompleted) "Selamat! Target \"${r.value.name}\" tercapai" else "Setoran dicatat")
                }
                is UseCaseResult.Failure -> contribution.update { it?.copy(error = r.message) }
            }
        }
    }

    fun openDetail(goal: SavingGoal) { detailId.value = goal.id }
    fun closeDetail() { detailId.value = null }
}

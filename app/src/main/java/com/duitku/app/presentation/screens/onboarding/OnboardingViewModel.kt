package com.duitku.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.DefaultCategories
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import com.duitku.app.domain.usecase.setup.CompleteOnboardingUseCase
import com.duitku.app.domain.usecase.setup.OnboardingInput
import com.duitku.app.domain.usecase.wallet.SaveWalletUseCase
import com.duitku.app.presentation.components.IconCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val ownerName: String = "",
    val walletName: String = "Dompet Utama",
    val walletType: WalletType = WalletType.CASH,
    val balanceInput: String = "",
    val selectedCategoryKeys: Set<String> = DefaultCategories.all.map { SeedDefaultCategoriesUseCase.key(it) }.toSet(),
    val monthStartDay: Int = 1,
    val retentionMonths: Int = 12,
    val loadDemoData: Boolean = false,
    val walletNameError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val isLastStep: Boolean get() = step == LAST_STEP

    companion object {
        const val LAST_STEP = 3
        const val STEP_COUNT = LAST_STEP + 1
    }
}

sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    fun onOwnerNameChange(value: String) = _state.update { it.copy(ownerName = value.take(40)) }

    fun onWalletNameChange(value: String) =
        _state.update { it.copy(walletName = value.take(SaveWalletUseCase.MAX_NAME + 5), walletNameError = null) }

    fun onWalletTypeChange(type: WalletType) = _state.update { it.copy(walletType = type) }

    fun onBalanceChange(value: String) =
        _state.update { it.copy(balanceInput = value.filter(Char::isDigit).trimStart('0').take(12)) }

    fun toggleCategory(key: String) = _state.update {
        val keys = if (key in it.selectedCategoryKeys) it.selectedCategoryKeys - key else it.selectedCategoryKeys + key
        it.copy(selectedCategoryKeys = keys, categoryError = null)
    }

    fun setAllCategories(selected: Boolean) = _state.update {
        it.copy(
            selectedCategoryKeys = if (selected) DefaultCategories.all.map(SeedDefaultCategoriesUseCase::key).toSet() else emptySet(),
            categoryError = null,
        )
    }

    fun onStartDayChange(day: Int) = _state.update {
        it.copy(monthStartDay = day.coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY))
    }

    fun onRetentionChange(months: Int) = _state.update { it.copy(retentionMonths = months) }
    fun onDemoChange(enabled: Boolean) = _state.update { it.copy(loadDemoData = enabled) }

    fun back() = _state.update { if (it.step > 0) it.copy(step = it.step - 1, saveError = null) else it }

    fun next() {
        val s = _state.value
        when (s.step) {
            1 -> if (s.walletName.isBlank()) {
                _state.update { it.copy(walletNameError = "Nama dompet wajib diisi") }
                return
            }
            2 -> if (s.selectedCategoryKeys.isEmpty() && !s.loadDemoData) {
                _state.update { it.copy(categoryError = "Pilih minimal satu kategori, atau tambahkan sendiri nanti") }
                return
            }
        }
        if (s.isLastStep) finish() else _state.update { it.copy(step = it.step + 1) }
    }

    /** Lewati pengaturan awal dengan nilai bawaan. */
    private fun finish() {
        val s = _state.value
        if (s.isSaving) return
        val magnitude = if (s.balanceInput.isEmpty()) 0L else MoneyFormatter.parse(s.balanceInput) ?: 0L
        val wallet = Wallet(
            name = s.walletName,
            type = s.walletType,
            initialBalance = magnitude,
            iconKey = IconCatalog.defaultIconFor(s.walletType),
            colorArgb = Palette.default,
        )
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = completeOnboarding(
                OnboardingInput(
                    ownerName = s.ownerName,
                    firstWallet = wallet,
                    selectedCategoryKeys = s.selectedCategoryKeys,
                    monthStartDay = s.monthStartDay,
                    retentionMonths = s.retentionMonths,
                    loadDemoData = s.loadDemoData,
                ),
            )
            when (result) {
                is UseCaseResult.Success -> _events.send(OnboardingEvent.Finished)
                is UseCaseResult.Failure -> _state.update { it.copy(isSaving = false, saveError = result.message) }
            }
        }
    }
}

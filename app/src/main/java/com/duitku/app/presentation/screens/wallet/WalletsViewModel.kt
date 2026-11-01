package com.duitku.app.presentation.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.WalletWithBalance
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WalletsUiState(
    val isLoading: Boolean = true,
    val active: List<WalletWithBalance> = emptyList(),
    val archived: List<WalletWithBalance> = emptyList(),
    val totalBalance: Long = 0,
    val currencyCode: String = "IDR",
)

@HiltViewModel
class WalletsViewModel @Inject constructor(
    walletRepository: WalletRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WalletsUiState> = combine(
        walletRepository.observeWalletsWithBalance(),
        settingsRepository.settings,
    ) { wallets, settings ->
        val (archived, active) = wallets.partition { it.wallet.isArchived }
        WalletsUiState(
            isLoading = false,
            active = active,
            archived = archived,
            // Saldo yang diarsipkan tetap dihitung agar total sama dengan jumlah seluruh uang Anda.
            totalBalance = wallets.sumOf { it.balance },
            currencyCode = settings.currencyCode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsUiState())
}

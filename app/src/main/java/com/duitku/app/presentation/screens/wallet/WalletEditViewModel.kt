package com.duitku.app.presentation.screens.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.WalletRepository
import com.duitku.app.domain.usecase.wallet.DeleteWalletResult
import com.duitku.app.domain.usecase.wallet.DeleteWalletUseCase
import com.duitku.app.domain.usecase.wallet.SaveWalletUseCase
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.IconCatalog
import com.duitku.app.presentation.navigation.Screen
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

data class WalletEditUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val isArchived: Boolean = false,
    val name: String = "",
    val type: WalletType = WalletType.CASH,
    val balanceInput: String = "",
    val balanceNegative: Boolean = false,
    val iconKey: String = "cash",
    val colorArgb: Int = Palette.default,
    val nameError: String? = null,
    val balanceError: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class WalletEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletRepository: WalletRepository,
    private val saveWallet: SaveWalletUseCase,
    private val deleteWallet: DeleteWalletUseCase,
) : ViewModel() {

    private val walletId: Long = savedStateHandle.get<Long>(Screen.WalletEdit.ARG_ID)?.takeIf { it > 0 } ?: 0L

    private val _state = MutableStateFlow(WalletEditUiState(isEdit = walletId != 0L))
    val state: StateFlow<WalletEditUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var original: Wallet? = null

    init {
        if (walletId == 0L) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val wallet = walletRepository.getWallet(walletId)
                if (wallet == null) {
                    _events.send(UiEvent.ShowMessage("Dompet tidak ditemukan"))
                    _events.send(UiEvent.NavigateBack)
                } else {
                    original = wallet
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isArchived = wallet.isArchived,
                            name = wallet.name,
                            type = wallet.type,
                            balanceInput = if (wallet.initialBalance == 0L) "" else kotlin.math.abs(wallet.initialBalance).toString(),
                            balanceNegative = wallet.initialBalance < 0,
                            iconKey = wallet.iconKey,
                            colorArgb = wallet.colorArgb,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(SaveWalletUseCase.MAX_NAME + 5), nameError = null) }

    fun onTypeChange(type: WalletType) = _state.update {
        // Ikon ikut berubah hanya jika masih memakai ikon bawaan jenis sebelumnya.
        val keepIcon = it.iconKey != IconCatalog.defaultIconFor(it.type)
        it.copy(
            type = type,
            iconKey = if (keepIcon) it.iconKey else IconCatalog.defaultIconFor(type),
            balanceNegative = if (type == WalletType.CREDIT_CARD) it.balanceNegative else false,
        )
    }

    fun onBalanceChange(value: String) = _state.update {
        val digits = value.filter(Char::isDigit).trimStart('0').take(12)
        it.copy(balanceInput = digits, balanceError = null)
    }

    fun onNegativeChange(negative: Boolean) = _state.update { it.copy(balanceNegative = negative) }
    fun onIconChange(key: String) = _state.update { it.copy(iconKey = key) }
    fun onColorChange(argb: Int) = _state.update { it.copy(colorArgb = argb) }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val magnitude = if (s.balanceInput.isEmpty()) 0L else MoneyFormatter.parse(s.balanceInput)
        if (magnitude == null) {
            _state.update { it.copy(balanceError = "Nominal tidak valid") }
            return
        }
        val wallet = Wallet(
            id = walletId,
            name = s.name,
            type = s.type,
            initialBalance = if (s.balanceNegative) -magnitude else magnitude,
            iconKey = s.iconKey,
            colorArgb = s.colorArgb,
            isArchived = original?.isArchived ?: false,
            sortOrder = original?.sortOrder ?: 0,
            openedAt = original?.openedAt ?: 0,
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = saveWallet(wallet)) {
                is UseCaseResult.Success -> {
                    _events.send(UiEvent.ShowMessage("Dompet disimpan"))
                    _events.send(UiEvent.NavigateBack)
                }
                is UseCaseResult.Failure -> {
                    _state.update { it.copy(isSaving = false, nameError = result.message) }
                }
            }
        }
    }

    fun toggleArchive() {
        val s = _state.value
        viewModelScope.launch {
            walletRepository.setArchived(walletId, !s.isArchived)
            _events.send(UiEvent.ShowMessage(if (s.isArchived) "Dompet diaktifkan kembali" else "Dompet diarsipkan"))
            _events.send(UiEvent.NavigateBack)
        }
    }

    fun delete() {
        viewModelScope.launch {
            when (val result = deleteWallet(walletId)) {
                DeleteWalletResult.Deleted -> {
                    _events.send(UiEvent.ShowMessage("Dompet dihapus"))
                    _events.send(UiEvent.NavigateBack)
                }
                is DeleteWalletResult.Archived -> {
                    _events.send(
                        UiEvent.ShowMessage("Dompet punya ${result.transactionCount} transaksi, jadi diarsipkan (riwayat tetap aman)"),
                    )
                    _events.send(UiEvent.NavigateBack)
                }
                is DeleteWalletResult.Failed -> _events.send(UiEvent.ShowMessage(result.message))
            }
        }
    }
}

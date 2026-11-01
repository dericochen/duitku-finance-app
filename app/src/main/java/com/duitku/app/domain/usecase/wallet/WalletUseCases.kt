package com.duitku.app.domain.usecase.wallet

import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.WalletRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveWalletUseCase @Inject constructor(
    private val repository: WalletRepository,
) {
    suspend operator fun invoke(wallet: Wallet): UseCaseResult<Long> {
        val name = wallet.name.trim()
        if (name.isEmpty()) return UseCaseResult.Failure("Nama dompet wajib diisi")
        if (name.length > MAX_NAME) return UseCaseResult.Failure("Nama dompet maksimal $MAX_NAME karakter")
        if (wallet.initialBalance < 0 && wallet.type != WalletType.CREDIT_CARD) {
            return UseCaseResult.Failure("Saldo awal tidak boleh negatif (kecuali kartu kredit)")
        }
        if (kotlin.math.abs(wallet.initialBalance) > TransactionRules.MAX_AMOUNT) {
            return UseCaseResult.Failure("Saldo awal terlalu besar")
        }
        val duplicate = repository.observeWallets().first()
            .any { it.id != wallet.id && it.name.equals(name, ignoreCase = true) }
        if (duplicate) return UseCaseResult.Failure("Sudah ada dompet dengan nama \"$name\"")

        return try {
            UseCaseResult.Success(repository.save(wallet.copy(name = name)))
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Gagal menyimpan dompet")
        }
    }

    companion object {
        const val MAX_NAME = 30
    }
}

sealed interface DeleteWalletResult {
    data object Deleted : DeleteWalletResult

    /** Dompet punya transaksi sehingga hanya diarsipkan agar riwayat tidak hilang. */
    data class Archived(val transactionCount: Int) : DeleteWalletResult
    data class Failed(val message: String) : DeleteWalletResult
}

class DeleteWalletUseCase @Inject constructor(
    private val repository: WalletRepository,
) {
    suspend operator fun invoke(id: Long): DeleteWalletResult = try {
        val count = repository.transactionCount(id)
        if (count > 0) {
            repository.setArchived(id, true)
            DeleteWalletResult.Archived(count)
        } else {
            repository.delete(id)
            DeleteWalletResult.Deleted
        }
    } catch (e: Exception) {
        DeleteWalletResult.Failed(e.message ?: "Gagal menghapus dompet")
    }
}

package com.duitku.app.domain.usecase.transaction

import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionError
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionItem
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.BudgetAlerts
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.PeriodClosedException
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Memvalidasi transaksi. Fungsi murni tanpa akses database sehingga mudah diuji. */
class ValidateTransactionUseCase @Inject constructor() {

    /**
     * @param amountEntered false bila pengguna belum mengetik nominal sama sekali.
     * @return daftar kesalahan; kosong berarti valid.
     */
    operator fun invoke(tx: Transaction, amountEntered: Boolean = true): List<TransactionError> {
        val errors = mutableListOf<TransactionError>()

        when {
            !amountEntered -> errors += TransactionError.AMOUNT_EMPTY
            tx.amount <= 0 -> errors += TransactionError.AMOUNT_NOT_POSITIVE
            tx.amount > TransactionRules.MAX_AMOUNT -> errors += TransactionError.AMOUNT_TOO_LARGE
        }

        if (tx.walletId <= 0) errors += TransactionError.WALLET_MISSING

        when (tx.type) {
            TransactionType.TRANSFER -> {
                val to = tx.toWalletId
                when {
                    to == null || to <= 0 -> errors += TransactionError.TO_WALLET_MISSING
                    to == tx.walletId -> errors += TransactionError.SAME_WALLET
                }
            }
            TransactionType.INCOME, TransactionType.EXPENSE -> {
                if (tx.categoryId == null || tx.categoryId <= 0) errors += TransactionError.CATEGORY_MISSING
            }
        }

        if (tx.note.length > TransactionRules.MAX_NOTE_LENGTH) errors += TransactionError.NOTE_TOO_LONG
        if (tx.tags.size > TransactionRules.MAX_TAGS) errors += TransactionError.TOO_MANY_TAGS
        if (tx.tags.any { it.length > TransactionRules.MAX_TAG_LENGTH }) errors += TransactionError.TAG_TOO_LONG

        val year = tx.occurredAt.toLocalDate().year
        if (year < TransactionRules.MIN_YEAR || year > TransactionRules.MAX_YEAR) {
            errors += TransactionError.DATE_OUT_OF_RANGE
        }
        return errors
    }
}

sealed interface SaveTransactionResult {
    data class Success(val id: Long) : SaveTransactionResult
    data class Invalid(val errors: List<TransactionError>) : SaveTransactionResult

    /** Tanggal jatuh pada periode yang sudah ditutup. UI menawarkan "Buka Kembali Bulan". */
    data class PeriodClosed(val periodYm: Int) : SaveTransactionResult
    data class Failed(val message: String) : SaveTransactionResult
}

class SaveTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
    private val validate: ValidateTransactionUseCase,
    private val budgetAlerts: BudgetAlerts,
) {
    suspend operator fun invoke(tx: Transaction, amountEntered: Boolean = true): SaveTransactionResult {
        // Normalisasi dulu: transfer tidak punya kategori; tag dibersihkan.
        val cleaned = tx.copy(
            note = tx.note.trim(),
            tags = TransactionRules.normalizeTags(tx.tags),
            categoryId = if (tx.type == TransactionType.TRANSFER) null else tx.categoryId,
            subCategoryId = if (tx.type == TransactionType.TRANSFER) null else tx.subCategoryId,
            toWalletId = if (tx.type == TransactionType.TRANSFER) tx.toWalletId else null,
        )
        val errors = validate(cleaned, amountEntered)
        if (errors.isNotEmpty()) return SaveTransactionResult.Invalid(errors)
        return try {
            val id = repository.save(cleaned)
            // Notifikasi anggaran tidak boleh menggagalkan penyimpanan.
            if (cleaned.type == TransactionType.EXPENSE) runCatching { budgetAlerts.check(cleaned.occurredAt) }
            SaveTransactionResult.Success(id)
        } catch (e: PeriodClosedException) {
            SaveTransactionResult.PeriodClosed(e.periodYm)
        } catch (e: Exception) {
            SaveTransactionResult.Failed(e.message ?: "Gagal menyimpan transaksi")
        }
    }
}

sealed interface DeleteTransactionResult {
    data class Deleted(val transaction: Transaction) : DeleteTransactionResult
    data class PeriodClosed(val periodYm: Int) : DeleteTransactionResult
    data object NotFound : DeleteTransactionResult
    data class Failed(val message: String) : DeleteTransactionResult
}

class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(id: Long): DeleteTransactionResult = try {
        repository.delete(id)?.let { DeleteTransactionResult.Deleted(it) } ?: DeleteTransactionResult.NotFound
    } catch (e: PeriodClosedException) {
        DeleteTransactionResult.PeriodClosed(e.periodYm)
    } catch (e: Exception) {
        DeleteTransactionResult.Failed(e.message ?: "Gagal menghapus transaksi")
    }
}

class UndoDeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transaction: Transaction): Boolean = try {
        repository.restore(transaction)
        true
    } catch (e: Exception) {
        false
    }
}

/** Menggabungkan transaksi dengan nama dompet/kategori untuk ditampilkan. */
object TransactionItemMapper {
    const val TRANSFER_ICON = "transfer"
    const val TRANSFER_COLOR = 0xFF1565C0.toInt()
    private const val FALLBACK_COLOR = 0xFF546E7A.toInt()

    fun map(
        transactions: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
    ): List<TransactionItem> {
        val walletById = wallets.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        return transactions.map { tx ->
            val wallet = walletById[tx.walletId]
            val category = tx.categoryId?.let(categoryById::get)
            val sub = tx.subCategoryId?.let(categoryById::get)
            val isTransfer = tx.type == TransactionType.TRANSFER
            TransactionItem(
                transaction = tx,
                walletName = wallet?.name ?: "Dompet dihapus",
                walletColorArgb = wallet?.colorArgb ?: FALLBACK_COLOR,
                toWalletName = tx.toWalletId?.let { walletById[it]?.name ?: "Dompet dihapus" },
                categoryName = if (isTransfer) "Transfer" else category?.name ?: "Tanpa kategori",
                subCategoryName = sub?.name,
                iconKey = if (isTransfer) TRANSFER_ICON else (sub?.iconKey ?: category?.iconKey ?: "other"),
                colorArgb = if (isTransfer) TRANSFER_COLOR else (category?.colorArgb ?: FALLBACK_COLOR),
            )
        }
    }
}

class ObserveTransactionItemsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val wallets: WalletRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(filter: TransactionFilter): Flow<List<TransactionItem>> =
        combine(
            transactions.observeFiltered(filter),
            wallets.observeWallets(),
            categories.observeAll(),
        ) { txs, ws, cs -> TransactionItemMapper.map(txs, ws, cs) }

    fun recent(limit: Int): Flow<List<TransactionItem>> =
        combine(
            transactions.observeRecent(limit),
            wallets.observeWallets(),
            categories.observeAll(),
        ) { txs, ws, cs -> TransactionItemMapper.map(txs, ws, cs) }
}

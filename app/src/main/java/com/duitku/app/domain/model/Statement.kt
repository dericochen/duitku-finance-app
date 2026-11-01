package com.duitku.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Isi snapshot (disimpan sebagai JSON di MonthlyStatement.snapshotJson) ----------
// Kunci JSON sengaja pendek agar baris database tetap kecil untuk ribuan transaksi.

@Serializable
data class SnapWallet(
    @SerialName("id") val id: Long,
    @SerialName("n") val name: String,
    @SerialName("o") val opening: Long,
    @SerialName("i") val income: Long,
    @SerialName("x") val expense: Long,
    @SerialName("ti") val transferIn: Long,
    @SerialName("to") val transferOut: Long,
    /** Saldo awal dompet yang dibuat di dalam periode ini (arus "dompet baru"). */
    @SerialName("a") val added: Long = 0,
    @SerialName("c") val closing: Long,
)

@Serializable
data class SnapCategory(
    @SerialName("id") val id: Long,
    @SerialName("n") val name: String,
    @SerialName("k") val kind: CategoryKind,
    @SerialName("p") val parentId: Long? = null,
)

@Serializable
data class SnapTransaction(
    @SerialName("id") val id: Long,
    @SerialName("ty") val type: TransactionType,
    @SerialName("am") val amount: Long,
    @SerialName("w") val walletId: Long,
    @SerialName("tw") val toWalletId: Long? = null,
    @SerialName("c") val categoryId: Long? = null,
    @SerialName("sc") val subCategoryId: Long? = null,
    @SerialName("nt") val note: String = "",
    @SerialName("tg") val tags: List<String> = emptyList(),
    @SerialName("at") val occurredAt: Long,
    @SerialName("rc") val recurring: Boolean = false,
)

@Serializable
data class SnapCategoryTotal(
    @SerialName("c") val categoryId: Long?,
    @SerialName("k") val kind: CategoryKind,
    @SerialName("t") val total: Long,
    @SerialName("n") val count: Int,
)

@Serializable
data class SnapBudget(
    @SerialName("c") val categoryId: Long?,
    @SerialName("am") val amount: Long,
    @SerialName("ri") val rollIn: Long,
    @SerialName("sp") val spent: Long,
    @SerialName("ro") val rollover: Boolean,
)

@Serializable
data class SnapGoal(
    @SerialName("id") val id: Long,
    @SerialName("n") val name: String,
    @SerialName("tg") val target: Long,
    @SerialName("sv") val saved: Long,
    @SerialName("dl") val deadline: Long? = null,
)

@Serializable
data class SnapDebt(
    @SerialName("id") val id: Long,
    @SerialName("k") val kind: DebtKind,
    @SerialName("p") val party: String,
    @SerialName("pr") val principal: Long,
    @SerialName("pd") val paid: Long,
    @SerialName("du") val due: Long? = null,
)

@Serializable
data class SnapRecurring(
    @SerialName("t") val title: String,
    @SerialName("ty") val type: TransactionType,
    @SerialName("am") val amount: Long,
    @SerialName("f") val frequency: RecurrenceFrequency,
    @SerialName("iv") val interval: Int,
    @SerialName("ac") val active: Boolean,
)

@Serializable
data class SnapTotals(
    @SerialName("o") val opening: Long,
    @SerialName("i") val income: Long,
    @SerialName("x") val expense: Long,
    @SerialName("tr") val transfer: Long,
    @SerialName("a") val added: Long = 0,
    @SerialName("c") val closing: Long,
    @SerialName("n") val net: Long,
    @SerialName("tc") val txCount: Int,
)

@Serializable
data class SnapPrevious(
    @SerialName("t") val totals: SnapTotals? = null,
    @SerialName("ce") val categoryExpense: List<SnapCategoryTotal> = emptyList(),
    @SerialName("ci") val categoryIncome: List<SnapCategoryTotal> = emptyList(),
)

@Serializable
data class StatementSnapshot(
    @SerialName("v") val version: Int = CURRENT_VERSION,
    @SerialName("p") val periodYm: Int,
    @SerialName("s") val startMillis: Long,
    @SerialName("e") val endMillis: Long,
    @SerialName("sd") val startDay: Int,
    @SerialName("cur") val currency: String,
    @SerialName("own") val owner: String,
    @SerialName("tot") val totals: SnapTotals,
    @SerialName("w") val wallets: List<SnapWallet>,
    @SerialName("c") val categories: List<SnapCategory>,
    @SerialName("t") val transactions: List<SnapTransaction>,
    @SerialName("ce") val expenseByCategory: List<SnapCategoryTotal>,
    @SerialName("ci") val incomeByCategory: List<SnapCategoryTotal>,
    @SerialName("b") val budgets: List<SnapBudget> = emptyList(),
    @SerialName("g") val goals: List<SnapGoal> = emptyList(),
    @SerialName("d") val debts: List<SnapDebt> = emptyList(),
    @SerialName("r") val recurring: List<SnapRecurring> = emptyList(),
    @SerialName("pv") val previous: SnapPrevious = SnapPrevious(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

// ---------- Model arsip untuk UI ----------

data class StatementSummary(
    val id: Long,
    val periodYm: Int,
    val startMillis: Long,
    val endMillis: Long,
    val openingBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val totalTransfer: Long,
    val closingBalance: Long,
    val netChange: Long,
    val transactionCount: Int,
    val status: StatementStatus,
    val closedAt: Long?,
    val revision: Int,
    val documentNumber: String,
    val checksum: String,
    val userNote: String,
) {
    val isClosed: Boolean get() = status == StatementStatus.CLOSED
    val revisionLabel: String get() = "Rev.$revision"
}

sealed interface CloseResult {
    data class Closed(val summary: StatementSummary) : CloseResult
    data object AlreadyClosed : CloseResult
    data object NoData : CloseResult
    data class Failed(val message: String) : CloseResult
}

sealed interface ReopenResult {
    data class Reopened(val periodYm: Int, val laterClosedPeriods: List<Int>) : ReopenResult
    data object NotClosed : ReopenResult
    data class Failed(val message: String) : ReopenResult
}

sealed interface VerifyResult {
    data class Valid(val checksum: String) : VerifyResult
    data class Tampered(val expected: String, val actual: String) : VerifyResult
    data object NotFound : VerifyResult
}

data class AuditEntry(
    val id: Long,
    val createdAt: Long,
    val periodYm: Int?,
    val entityType: String,
    val entityId: Long?,
    val action: String,
    val description: String,
    val oldValue: String?,
    val newValue: String?,
)

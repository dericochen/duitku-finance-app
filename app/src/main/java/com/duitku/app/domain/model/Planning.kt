package com.duitku.app.domain.model

import java.time.LocalDate

// ---------- Transaksi berulang ----------

data class RecurringTransaction(
    val id: Long = 0,
    val title: String,
    val type: TransactionType,
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val frequency: RecurrenceFrequency,
    val intervalCount: Int = 1,
    val startAt: Long,
    val endAt: Long? = null,
    /** Jadwal berikutnya yang belum dieksekusi. */
    val nextDueAt: Long,
    val lastRunAt: Long? = null,
    val isActive: Boolean = true,
) {
    val frequencyLabel: String
        get() = if (intervalCount <= 1) frequency.label else "Setiap $intervalCount ${unit(frequency)}"

    private fun unit(f: RecurrenceFrequency) = when (f) {
        RecurrenceFrequency.DAILY -> "hari"
        RecurrenceFrequency.WEEKLY -> "minggu"
        RecurrenceFrequency.MONTHLY -> "bulan"
        RecurrenceFrequency.YEARLY -> "tahun"
    }
}

data class RecurringRunResult(
    val created: Int = 0,
    /** Dilewati karena tanggalnya jatuh di bulan yang sudah ditutup. */
    val skippedClosed: Int = 0,
    val failed: Int = 0,
) {
    val isEmpty: Boolean get() = created == 0 && skippedClosed == 0 && failed == 0
}

// ---------- Target tabungan ----------

data class SavingGoal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val savedAmount: Long = 0,
    val deadline: Long? = null,
    val iconKey: String = "savings",
    val colorArgb: Int = Palette.default,
    val note: String = "",
    val createdAt: Long = 0,
    val completedAt: Long? = null,
) {
    val remaining: Long get() = (targetAmount - savedAmount).coerceAtLeast(0)
    val percent: Int get() = if (targetAmount <= 0) 0 else ((savedAmount * 100) / targetAmount).coerceIn(0, 100).toInt()
    val isCompleted: Boolean get() = savedAmount >= targetAmount
}

data class GoalContribution(
    val id: Long = 0,
    val goalId: Long,
    /** Positif = setoran, negatif = penarikan. */
    val amount: Long,
    val contributedAt: Long,
    val note: String = "",
)

data class GoalEstimate(
    /** Perkiraan tanggal tercapai berdasarkan laju menabung 90 hari terakhir; null bila belum ada laju. */
    val estimatedDate: LocalDate?,
    /** Setoran per bulan agar tepat waktu sampai tenggat; null bila tanpa tenggat atau sudah lewat. */
    val neededPerMonth: Long?,
    val onTrack: Boolean?,
)

// ---------- Utang & piutang ----------

enum class DebtState(val label: String) {
    PAID("Lunas"),
    OVERDUE("Terlambat"),
    DUE_SOON("Segera jatuh tempo"),
    ACTIVE("Aktif"),
}

data class Debt(
    val id: Long = 0,
    val kind: DebtKind,
    val partyName: String,
    val principalAmount: Long,
    val dueDate: Long? = null,
    val note: String = "",
    val isPaidOff: Boolean = false,
    val reminderEnabled: Boolean = true,
    val createdAt: Long = 0,
    val paidOffAt: Long? = null,
)

data class DebtPayment(
    val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val paidAt: Long,
    val note: String = "",
)

data class DebtWithPaid(
    val debt: Debt,
    val paidAmount: Long,
) {
    val remaining: Long get() = (debt.principalAmount - paidAmount).coerceAtLeast(0)
    val percent: Int get() = if (debt.principalAmount <= 0) 0 else ((paidAmount * 100) / debt.principalAmount).coerceIn(0, 100).toInt()
}

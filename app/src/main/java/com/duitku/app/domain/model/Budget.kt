package com.duitku.app.domain.model

data class Budget(
    val id: Long = 0,
    val periodYm: Int,
    /** null = anggaran total bulanan. */
    val categoryId: Long? = null,
    val amount: Long,
    val rollover: Boolean = false,
)

enum class BudgetStatus(val label: String) {
    SAFE("Aman"),
    WARNING("Waspada"),
    EXCEEDED("Melebihi"),
}

data class BudgetLine(
    val budget: Budget,
    /** Sisa anggaran bulan lalu yang dibawa ke bulan ini. */
    val rollIn: Long,
    val spent: Long,
) {
    val limit: Long get() = budget.amount + rollIn
    val remaining: Long get() = limit - spent

    /** Persentase terpakai, dibulatkan ke bawah (bisa > 100). Limit 0 dianggap 100% bila ada pengeluaran. */
    val percent: Int
        get() = when {
            limit <= 0L -> if (spent > 0) 100 else 0
            else -> ((spent * 100) / limit).coerceAtMost(9_999).toInt()
        }

    val status: BudgetStatus
        get() = when {
            percent >= 100 -> BudgetStatus.EXCEEDED
            percent >= WARNING_PERCENT -> BudgetStatus.WARNING
            else -> BudgetStatus.SAFE
        }

    val isTotal: Boolean get() = budget.categoryId == null

    companion object {
        const val WARNING_PERCENT = 80
    }
}

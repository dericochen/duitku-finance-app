package com.duitku.app.domain.model

import com.duitku.app.domain.usecase.statement.Reconciliation

/** Ringkasan satu periode, untuk semua dompet atau satu dompet. */
data class MonthSummary(
    val opening: Long,
    val income: Long,
    val expense: Long,
    val transferIn: Long,
    val transferOut: Long,
    /** Saldo awal dompet yang dibuat dalam periode ini. */
    val added: Long,
    val closing: Long,
    val txCount: Int,
) {
    val net: Long get() = income - expense
}

/** Satu baris mutasi beserta saldo berjalan setelah transaksi tersebut. */
data class MonthRow(
    val item: TransactionItem,
    val balanceAfter: Long,
)

data class LiveMonth(
    val periodYm: Int,
    val startMillis: Long,
    val endMillis: Long,
    val walletId: Long?,
    val summary: MonthSummary,
    val walletFlows: List<SnapWallet>,
    /** Urut terbaru dulu. */
    val rows: List<MonthRow>,
    val reconciliation: Reconciliation.Result,
)

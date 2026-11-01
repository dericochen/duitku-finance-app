package com.duitku.app.domain.usecase.statement

import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.LiveMonth
import com.duitku.app.domain.model.MonthRow
import com.duitku.app.domain.model.MonthSummary
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.usecase.transaction.TransactionItemMapper

/** Merakit tampilan mutasi bulanan (ringkasan + baris dengan saldo berjalan) dari data mentah. */
object LiveMonthBuilder {

    fun build(
        periodYm: Int,
        startMillis: Long,
        endMillis: Long,
        walletId: Long?,
        txsAscending: List<Transaction>,
        wallets: List<Wallet>,
        balancesBefore: Map<Long, Long>,
        categories: List<Category>,
        balancesAtEnd: Map<Long, Long>? = null,
    ): LiveMonth {
        val flows = PeriodMath.walletFlows(wallets, balancesBefore, txsAscending, startMillis, endMillis, balancesAtEnd)
        return assemble(periodYm, startMillis, endMillis, walletId, txsAscending, wallets, flows, categories)
    }

    /**
     * Tampilan bulan tertutup yang bersumber dari snapshot arsip (tidak bergantung pada tabel transaksi),
     * sehingga tetap utuh walau transaksi mentah sudah dibersihkan oleh retensi.
     */
    fun fromSnapshot(snapshot: StatementSnapshot, walletId: Long?): LiveMonth {
        val wallets = snapshot.wallets.map { Wallet(it.id, it.name, WalletType.BANK, 0, "bank", DEFAULT_COLOR) }
        val categories = snapshot.categories.map { Category(it.id, it.name, it.kind, it.parentId, "other", DEFAULT_COLOR) }
        val txs = snapshot.transactions.map {
            Transaction(
                id = it.id, type = it.type, amount = it.amount, walletId = it.walletId, toWalletId = it.toWalletId,
                categoryId = it.categoryId, subCategoryId = it.subCategoryId, note = it.note, tags = it.tags,
                occurredAt = it.occurredAt, recurringId = if (it.recurring) 1L else null,
            )
        }.sortedWith(compareBy({ it.occurredAt }, { it.id }))
        return assemble(snapshot.periodYm, snapshot.startMillis, snapshot.endMillis, walletId, txs, wallets, snapshot.wallets, categories)
    }

    private const val DEFAULT_COLOR = 0xFF546E7A.toInt()

    private fun assemble(
        periodYm: Int,
        startMillis: Long,
        endMillis: Long,
        walletId: Long?,
        txsAscending: List<Transaction>,
        wallets: List<Wallet>,
        flows: List<com.duitku.app.domain.model.SnapWallet>,
        categories: List<Category>,
    ): LiveMonth {
        val single = walletId?.let { id -> flows.firstOrNull { it.id == id } }

        val relevant = if (single != null) {
            txsAscending.filter { it.walletId == single.id || it.toWalletId == single.id }
        } else {
            txsAscending
        }

        val summary: MonthSummary
        val startBalance: Long
        val reconciliation: Reconciliation.Result
        if (single != null) {
            summary = MonthSummary(
                opening = single.opening, income = single.income, expense = single.expense,
                transferIn = single.transferIn, transferOut = single.transferOut, added = single.added,
                closing = single.closing, txCount = relevant.size,
            )
            startBalance = single.opening + single.added
            reconciliation = Reconciliation.check(
                single.opening, single.income + single.transferIn, single.expense + single.transferOut,
                single.added, single.closing,
            )
        } else {
            val totals = PeriodMath.totals(txsAscending, flows)
            summary = MonthSummary(
                opening = totals.opening, income = totals.income, expense = totals.expense,
                transferIn = totals.transfer, transferOut = totals.transfer, added = totals.added,
                closing = totals.closing, txCount = totals.txCount,
            )
            startBalance = totals.opening + totals.added
            reconciliation = Reconciliation.check(totals)
        }

        val running = RunningBalance.compute(startBalance, relevant, single?.id)
        val items = TransactionItemMapper.map(running.map { it.transaction }, wallets, categories)
        val rows = items.mapIndexed { i, item -> MonthRow(item, running[i].balanceAfter) }.asReversed()

        return LiveMonth(
            periodYm = periodYm, startMillis = startMillis, endMillis = endMillis, walletId = single?.id,
            summary = summary, walletFlows = flows, rows = rows, reconciliation = reconciliation,
        )
    }
}

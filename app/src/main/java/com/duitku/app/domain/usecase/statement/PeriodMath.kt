package com.duitku.app.domain.usecase.statement

import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.SnapCategoryTotal
import com.duitku.app.domain.model.SnapTotals
import com.duitku.app.domain.model.SnapWallet
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import java.security.MessageDigest

/**
 * Perhitungan murni (tanpa database) untuk mutasi satu periode. Dipakai tampilan langsung,
 * pembuatan snapshot, dan PDF sehingga angka di semua tempat selalu sama.
 */
object PeriodMath {

    /**
     * Arus & saldo tiap dompet.
     * @param balancesBefore saldo tiap dompet tepat sebelum periode dimulai (dari DAO.getBalancesBefore).
     * @param txs transaksi yang berada di dalam periode.
     */
    fun walletFlows(
        wallets: List<Wallet>,
        balancesBefore: Map<Long, Long>,
        txs: List<Transaction>,
        startMillis: Long,
        endMillis: Long,
        closingBalances: Map<Long, Long>? = null,
    ): List<SnapWallet> = wallets.map { w ->
        var income = 0L
        var expense = 0L
        var tIn = 0L
        var tOut = 0L
        for (t in txs) {
            when (t.type) {
                TransactionType.INCOME -> if (t.walletId == w.id) income += t.amount
                TransactionType.EXPENSE -> if (t.walletId == w.id) expense += t.amount
                TransactionType.TRANSFER -> {
                    if (t.walletId == w.id) tOut += t.amount
                    if (t.toWalletId == w.id) tIn += t.amount
                }
            }
        }
        val opening = balancesBefore[w.id] ?: 0L
        val added = if (w.openedAt in startMillis..endMillis) w.initialBalance else 0L
        SnapWallet(
            id = w.id, name = w.name, opening = opening, income = income, expense = expense,
            transferIn = tIn, transferOut = tOut, added = added,
            // Saldo akhir sebaiknya dihitung terpisah (kueri saldo di akhir periode) agar rekonsiliasi bermakna.
            closing = closingBalances?.get(w.id) ?: (opening + income - expense + tIn - tOut + added),
        )
    }

    fun totals(txs: List<Transaction>, wallets: List<SnapWallet>): SnapTotals {
        val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val transfer = txs.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val opening = wallets.sumOf { it.opening }
        val added = wallets.sumOf { it.added }
        return SnapTotals(
            opening = opening, income = income, expense = expense, transfer = transfer, added = added,
            closing = wallets.sumOf { it.closing }, net = income - expense, txCount = txs.size,
        )
    }

    fun categoryTotals(txs: List<Transaction>, kind: CategoryKind): List<SnapCategoryTotal> {
        val type = if (kind == CategoryKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        return txs.filter { it.type == type }
            .groupBy { it.categoryId }
            .map { (id, list) -> SnapCategoryTotal(id, kind, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.total }
    }
}

/** Saldo berjalan per transaksi. */
object RunningBalance {
    data class Row(val transaction: Transaction, val balanceAfter: Long)

    /**
     * @param startBalance saldo sebelum transaksi pertama (saldo awal [+ saldo awal dompet baru]).
     * @param txsAscending transaksi urut waktu naik.
     * @param walletId null = total semua dompet (transfer netral), terisi = hanya dompet tersebut.
     */
    fun compute(startBalance: Long, txsAscending: List<Transaction>, walletId: Long? = null): List<Row> {
        var balance = startBalance
        val rows = ArrayList<Row>(txsAscending.size)
        for (t in txsAscending) {
            val delta = if (walletId == null) {
                when (t.type) {
                    TransactionType.INCOME -> t.amount
                    TransactionType.EXPENSE -> -t.amount
                    TransactionType.TRANSFER -> 0L
                }
            } else {
                t.effectOn(walletId)
            }
            if (walletId != null && t.walletId != walletId && t.toWalletId != walletId) continue
            balance += delta
            rows += Row(t, balance)
        }
        return rows
    }
}

/** Rekonsiliasi: saldo awal + masuk - keluar (+ dompet baru) harus sama dengan saldo akhir. */
object Reconciliation {
    data class Result(val expectedClosing: Long, val actualClosing: Long, val difference: Long) {
        val isBalanced: Boolean get() = difference == 0L
    }

    data class BankResult(val appBalance: Long, val bankBalance: Long, val difference: Long) {
        val isMatch: Boolean get() = difference == 0L
    }

    fun check(opening: Long, income: Long, expense: Long, added: Long, closing: Long): Result {
        val expected = opening + income - expense + added
        return Result(expected, closing, closing - expected)
    }

    fun check(totals: SnapTotals): Result =
        check(totals.opening, totals.income, totals.expense, totals.added, totals.closing)

    /** Selisih saldo catatan DuitKu dengan saldo di rekening bank (positif = catatan lebih besar). */
    fun againstBank(appBalance: Long, bankBalance: Long) =
        BankResult(appBalance, bankBalance, appBalance - bankBalance)
}

object Checksum {
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 12 karakter pertama, dicetak di PDF. */
    fun short(hex: String): String = hex.take(12).uppercase()
}

object DocumentNumber {
    /** Contoh: DKU-202611-0001. [sequence] = nomor urut dokumen pada periode tersebut. */
    fun format(periodYm: Int, sequence: Int): String = "DKU-%06d-%04d".format(periodYm, sequence)
}

package com.duitku.app.domain.usecase.report

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.model.BudgetReport
import com.duitku.app.domain.model.BudgetReportLine
import com.duitku.app.domain.model.BudgetStatus
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.CategoryAnalysisRow
import com.duitku.app.domain.model.ClosingInfo
import com.duitku.app.domain.model.Comparison
import com.duitku.app.domain.model.ExecutiveSummary
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.ReconciliationReport
import com.duitku.app.domain.model.RecurringReport
import com.duitku.app.domain.model.ReportDay
import com.duitku.app.domain.model.ReportMeta
import com.duitku.app.domain.model.ReportModel
import com.duitku.app.domain.model.ReportRow
import com.duitku.app.domain.model.ReportTx
import com.duitku.app.domain.model.SnapCategoryTotal
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.model.SnapTransaction
import com.duitku.app.domain.model.SnapWallet
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.TrendData
import com.duitku.app.domain.model.WalletReconciliation
import com.duitku.app.domain.model.WalletSummaryRow
import com.duitku.app.domain.usecase.planning.RecurringMath
import com.duitku.app.domain.usecase.statement.RunningBalance
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Informasi dokumen yang menyertai snapshot (nomor, status, revisi, checksum). */
data class DocumentInfo(
    val documentNumber: String,
    val isFinal: Boolean,
    val revisionLabel: String,
    val checksum: String,
    val userNote: String,
    val printedAt: Long,
)

/**
 * Merakit seluruh isi laporan PDF bulanan dari snapshot. Fungsi murni: tidak menyentuh database,
 * sehingga laporan bulan tertutup selalu bersumber dari arsip yang tidak berubah.
 */
object ReportBuilder {

    fun build(
        snapshot: StatementSnapshot,
        options: ExportOptions,
        doc: DocumentInfo,
        today: LocalDate = LocalDate.now(),
    ): ReportModel {
        val walletId = options.walletId
        val walletsAll = snapshot.wallets
        val single = walletId?.let { id -> walletsAll.firstOrNull { it.id == id } }
        val walletName = walletsAll.associate { it.id to it.name }
        val categoryById = snapshot.categories.associateBy { it.id }

        fun label(tx: SnapTransaction): String {
            if (tx.type == TransactionType.TRANSFER) return "Transfer"
            val parent = tx.categoryId?.let(categoryById::get)?.name ?: "Tanpa kategori"
            val sub = tx.subCategoryId?.let(categoryById::get)?.name
            return if (sub != null) "$parent > $sub" else parent
        }

        fun toReport(tx: SnapTransaction) = ReportTx(
            id = tx.id, type = tx.type, amount = tx.amount, at = tx.occurredAt,
            walletName = walletName[tx.walletId] ?: "Dompet dihapus",
            toWalletName = tx.toWalletId?.let { walletName[it] ?: "Dompet dihapus" },
            categoryLabel = label(tx), note = tx.note, tags = tx.tags, recurring = tx.recurring,
        )

        val txs = snapshot.transactions
            .filter { single == null || it.walletId == single.id || it.toWalletId == single.id }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))

        // ----- Rincian mutasi + saldo berjalan -----
        val startBalance = if (single != null) single.opening + single.added else snapshot.totals.opening + snapshot.totals.added
        val domainTx = txs.map {
            Transaction(
                id = it.id, type = it.type, amount = it.amount, walletId = it.walletId, toWalletId = it.toWalletId,
                occurredAt = it.occurredAt,
            )
        }
        val running = RunningBalance.compute(startBalance, domainTx, single?.id)
        val rows = txs.mapIndexed { index, tx ->
            val neutral = single == null && tx.type == TransactionType.TRANSFER
            val debit: Long
            val credit: Long
            when {
                neutral -> { debit = 0; credit = 0 }
                tx.type == TransactionType.INCOME -> { debit = 0; credit = tx.amount }
                tx.type == TransactionType.EXPENSE -> { debit = tx.amount; credit = 0 }
                tx.walletId == single?.id -> { debit = tx.amount; credit = 0 }  // transfer keluar
                else -> { debit = 0; credit = tx.amount }                         // transfer masuk
            }
            ReportRow(index + 1, toReport(tx), debit, credit, neutral, running[index].balanceAfter)
        }
        val days = rows.groupBy { it.tx.at.toLocalDate() }.toSortedMap().map { (date, list) ->
            ReportDay(date, list, list.sumOf { it.debit }, list.sumOf { it.credit })
        }

        // ----- Ringkasan angka -----
        val income = single?.income ?: snapshot.totals.income
        val expense = single?.expense ?: snapshot.totals.expense
        val transferIn = single?.transferIn ?: snapshot.totals.transfer
        val transferOut = single?.transferOut ?: snapshot.totals.transfer
        val opening = single?.opening ?: snapshot.totals.opening
        val added = single?.added ?: snapshot.totals.added
        val closing = single?.closing ?: snapshot.totals.closing

        val periodStart = snapshot.startMillis.toLocalDate()
        val periodEnd = snapshot.endMillis.toLocalDate()
        val totalDays = (ChronoUnit.DAYS.between(periodStart, periodEnd) + 1).toInt().coerceAtLeast(1)
        // Untuk bulan yang belum selesai, rata-rata dihitung dari hari yang sudah berjalan.
        val elapsed = if (today.isBefore(periodEnd)) {
            (ChronoUnit.DAYS.between(periodStart, today) + 1).toInt().coerceIn(1, totalDays)
        } else {
            totalDays
        }

        val expenseTx = txs.filter { it.type == TransactionType.EXPENSE && (single == null || it.walletId == single.id) }
        val incomeTx = txs.filter { it.type == TransactionType.INCOME && (single == null || it.walletId == single.id) }
        val dayExpense = expenseTx.groupBy { it.occurredAt.toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val priciestDay = dayExpense.maxByOrNull { it.value }?.let { it.key to it.value }

        // ----- Analisis kategori -----
        val expenseAnalysis = analyze(expenseTx, categoryById, snapshot.previous.categoryExpense.takeIf { single == null }.orEmpty())
        val incomeAnalysis = analyze(incomeTx, categoryById, snapshot.previous.categoryIncome.takeIf { single == null }.orEmpty())

        val prev = snapshot.previous.totals.takeIf { single == null }
        val expenseCmp = prev?.let { Comparison(expense, it.expense) }
        val incomeCmp = prev?.let { Comparison(income, it.income) }
        val budget = buildBudget(snapshot, categoryById)

        val summary = ExecutiveSummary(
            opening = opening, income = income, expense = expense, transferIn = transferIn, transferOut = transferOut,
            added = added, closing = closing, net = income - expense,
            savingsRate = if (income > 0) (income - expense) * 100.0 / income else null,
            dayCount = totalDays, avgDailyExpense = expense / elapsed,
            mostExpensiveDay = priciestDay,
            biggestExpense = expenseTx.maxByOrNull { it.amount }?.let(::toReport),
            biggestIncome = incomeTx.maxByOrNull { it.amount }?.let(::toReport),
            expenseVsPrevious = expenseCmp, incomeVsPrevious = incomeCmp,
            conclusion = "",
        ).let { it.copy(conclusion = ConclusionWriter.write(it, expenseAnalysis.firstOrNull(), budget, snapshot.currency, DateFormats.monthYear(snapshot.periodYm.toYearMonth()))) }

        // ----- Rekonsiliasi -----
        val walletRecon = (if (single != null) listOf(single) else walletsAll).map { w ->
            WalletReconciliation(
                name = w.name,
                expected = w.opening + w.income - w.expense + w.transferIn - w.transferOut + w.added,
                recorded = w.closing,
                bankBalance = options.bankBalances[w.id],
            )
        }
        val reconciliation = ReconciliationReport(
            opening = opening, income = income, expense = expense, added = added,
            expectedClosing = opening + income - expense + added +
                if (single != null) transferIn - transferOut else 0L,
            recordedClosing = closing, wallets = walletRecon,
        )

        val meta = ReportMeta(
            title = "LAPORAN MUTASI KEUANGAN PRIBADI",
            ownerName = snapshot.owner.ifBlank { "-" },
            periodLabel = DateFormats.monthYear(snapshot.periodYm.toYearMonth()),
            rangeLabel = "${DateFormats.dateLong(periodStart)} - ${DateFormats.dateLong(periodEnd)}",
            documentNumber = doc.documentNumber, printedAt = doc.printedAt, isFinal = doc.isFinal,
            revisionLabel = doc.revisionLabel, currency = snapshot.currency,
            walletScope = single?.name ?: "Semua dompet", checksum = doc.checksum,
            startMillis = snapshot.startMillis, endMillis = snapshot.endMillis,
        )

        return ReportModel(
            meta = meta,
            summary = summary,
            wallets = (if (single != null) listOf(single) else walletsAll).map(::walletRow),
            days = days,
            expenseAnalysis = expenseAnalysis,
            incomeAnalysis = incomeAnalysis,
            budget = budget,
            trends = trends(snapshot, txs, single, startBalance, periodStart, periodEnd, today),
            topExpense = expenseTx.sortedByDescending { it.amount }.take(10).map(::toReport),
            topIncome = incomeTx.sortedByDescending { it.amount }.take(10).map(::toReport),
            recurring = RecurringReport(
                items = snapshot.recurring,
                monthlyBurden = snapshot.recurring.filter { it.active && it.type == TransactionType.EXPENSE }
                    .sumOf { RecurringMath.monthlyEquivalent(it.amount, it.frequency, it.interval) },
            ),
            goals = snapshot.goals,
            debts = snapshot.debts,
            reconciliation = reconciliation,
            closing = ClosingInfo(
                userNote = options.userNote ?: doc.userNote,
                documentNumber = doc.documentNumber, checksum = doc.checksum,
                qrPayload = qrPayload(doc.documentNumber, doc.checksum),
            ),
        )
    }

    fun qrPayload(documentNumber: String, checksum: String) = "DUITKU|$documentNumber|$checksum"

    private fun walletRow(w: SnapWallet) = WalletSummaryRow(
        w.name, w.opening, w.income, w.expense, w.transferIn, w.transferOut, w.added, w.closing,
    )

    private fun analyze(
        txs: List<SnapTransaction>,
        categories: Map<Long, com.duitku.app.domain.model.SnapCategory>,
        previous: List<SnapCategoryTotal>,
    ): List<CategoryAnalysisRow> {
        val total = txs.sumOf { it.amount }
        if (total <= 0) return emptyList()
        val prev = previous.associate { it.categoryId to it.total }
        val palette = com.duitku.app.domain.model.Palette.colors
        return txs.groupBy { it.categoryId }.map { (id, list) ->
            val sum = list.sumOf { it.amount }
            CategoryAnalysisRow(
                name = id?.let { categories[it]?.name } ?: "Tanpa kategori",
                count = list.size, total = sum, percent = Math.round(sum * 1000.0 / total) / 10.0,
                average = sum / list.size, previousTotal = prev[id] ?: 0L,
                colorArgb = palette[(id ?: 0L).toInt().mod(palette.size)],
            )
        }.sortedByDescending { it.total }
    }

    private fun buildBudget(snapshot: StatementSnapshot, categories: Map<Long, com.duitku.app.domain.model.SnapCategory>): BudgetReport? {
        if (snapshot.budgets.isEmpty()) return null
        val lines = snapshot.budgets.map { b ->
            val line = BudgetLine(Budget(periodYm = snapshot.periodYm, categoryId = b.categoryId, amount = b.amount, rollover = b.rollover), b.rollIn, b.spent)
            BudgetReportLine(
                name = if (b.categoryId == null) "Total bulanan" else categories[b.categoryId]?.name ?: "Kategori dihapus",
                amount = b.amount, rollIn = b.rollIn, spent = b.spent, remaining = line.remaining,
                percent = line.percent, status = line.status, rollover = b.rollover, isTotal = b.categoryId == null,
            )
        }.sortedWith(compareByDescending<BudgetReportLine> { it.isTotal }.thenBy { it.name })
        return BudgetReport(lines, lines.filter { it.rollover && it.remaining > 0 }.map { it.name to it.remaining })
    }

    private fun trends(
        snapshot: StatementSnapshot,
        txs: List<SnapTransaction>,
        single: SnapWallet?,
        startBalance: Long,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): TrendData {
        fun delta(t: SnapTransaction): Long = when (t.type) {
            TransactionType.INCOME -> if (single == null || t.walletId == single.id) t.amount else 0L
            TransactionType.EXPENSE -> if (single == null || t.walletId == single.id) -t.amount else 0L
            TransactionType.TRANSFER -> when {
                single == null -> 0L
                t.walletId == single.id -> -t.amount
                t.toWalletId == single.id -> t.amount
                else -> 0L
            }
        }
        val byDay = txs.groupBy { it.occurredAt.toLocalDate() }
        val last = if (today.isBefore(end) && !today.isBefore(start)) today else end
        val cash = ArrayList<Pair<LocalDate, Long>>()
        val balance = ArrayList<Pair<LocalDate, Long>>()
        var running = startBalance
        var d = start
        while (!d.isAfter(last)) {
            val net = byDay[d].orEmpty().filter { it.type != TransactionType.TRANSFER }.sumOf(::delta)
            running += byDay[d].orEmpty().sumOf(::delta)
            cash += d to net
            balance += d to running
            d = d.plusDays(1)
        }

        val weeks = (ChronoUnit.DAYS.between(start, end) / 7 + 1).toInt()
        val weekly = (0 until weeks).map { w ->
            val from = start.plusDays(w * 7L)
            val to = from.plusDays(6)
            val inWeek = txs.filter { val day = it.occurredAt.toLocalDate(); !day.isBefore(from) && !day.isAfter(to) }
            Triple(
                "Mgg ${w + 1}",
                inWeek.filter { it.type == TransactionType.INCOME && (single == null || it.walletId == single.id) }.sumOf { it.amount },
                inWeek.filter { it.type == TransactionType.EXPENSE && (single == null || it.walletId == single.id) }.sumOf { it.amount },
            )
        }
        val names = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        val weekdayTotals = LongArray(7)
        txs.filter { it.type == TransactionType.EXPENSE && (single == null || it.walletId == single.id) }.forEach {
            weekdayTotals[it.occurredAt.toLocalDate().dayOfWeek.value - 1] += it.amount
        }
        return TrendData(cash, balance, weekly, names.mapIndexed { i, n -> n to weekdayTotals[i] })
    }
}

/** Menulis kesimpulan singkat berbahasa Indonesia dari angka-angka ringkasan. */
object ConclusionWriter {
    fun write(
        s: ExecutiveSummary,
        topCategory: CategoryAnalysisRow?,
        budget: BudgetReport?,
        currency: String,
        periodLabel: String,
    ): String {
        fun money(v: Long) = MoneyFormatter.format(v, currency)
        fun pct(v: Double) = String.format(Locale.forLanguageTag("id-ID"), "%.1f", v)
        val parts = mutableListOf<String>()

        parts += "Pada $periodLabel, pemasukan tercatat ${money(s.income)} dan pengeluaran ${money(s.expense)}."
        parts += when {
            s.net > 0 -> "Arus kas bersih positif ${money(s.net)}" + (s.savingsRate?.let { ", artinya ${pct(it)}% pemasukan berhasil disisihkan." } ?: ".")
            s.net < 0 -> "Arus kas bersih negatif ${money(-s.net)}: pengeluaran melebihi pemasukan sehingga saldo berkurang."
            else -> "Arus kas bersih seimbang (pemasukan sama dengan pengeluaran)."
        }
        s.expenseVsPrevious?.percent?.let { p ->
            parts += when {
                p > 0.05 -> "Pengeluaran naik ${pct(p)}% dibanding bulan lalu."
                p < -0.05 -> "Pengeluaran turun ${pct(-p)}% dibanding bulan lalu."
                else -> "Pengeluaran relatif sama dengan bulan lalu."
            }
        }
        topCategory?.let { parts += "Pengeluaran terbesar ada pada kategori ${it.name} (${pct(it.percent)}% dari total pengeluaran)." }
        s.mostExpensiveDay?.let { (date, amount) -> parts += "Hari paling boros ${DateFormats.dayHeader(date)} sebesar ${money(amount)}." }
        budget?.lines?.firstOrNull { it.isTotal }?.let { b ->
            parts += when (b.status) {
                BudgetStatus.EXCEEDED -> "Anggaran total terlampaui (${b.percent}% terpakai)."
                BudgetStatus.WARNING -> "Anggaran total hampir habis (${b.percent}% terpakai)."
                BudgetStatus.SAFE -> "Anggaran total masih aman (${b.percent}% terpakai)."
            }
        }
        val exceeded = budget?.lines?.count { !it.isTotal && it.status == BudgetStatus.EXCEEDED } ?: 0
        if (exceeded > 0) parts += "$exceeded kategori melebihi anggaran."
        return parts.joinToString(" ")
    }
}

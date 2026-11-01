package com.duitku.app.domain.usecase.report

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.AnnualMonthRow
import com.duitku.app.domain.model.AnnualReportModel
import com.duitku.app.domain.model.CategoryAnalysisRow
import com.duitku.app.domain.model.ClosingInfo
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.ReportMeta
import com.duitku.app.domain.model.SnapCategoryTotal
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.usecase.statement.Checksum

data class AnnualInput(val snapshot: StatementSnapshot, val isClosed: Boolean, val checksum: String)

/** Laporan rentang bebas / tahunan: ringkasan per bulan, total, rata-rata, kategori terbesar, bulan terboros/terhemat. */
object AnnualReportBuilder {

    fun build(
        inputs: List<AnnualInput>,
        title: String,
        ownerName: String,
        printedAt: Long,
        currency: String,
        userNote: String = "",
    ): AnnualReportModel {
        require(inputs.isNotEmpty()) { "Tidak ada periode untuk dilaporkan" }
        val sorted = inputs.sortedBy { it.snapshot.periodYm }
        val first = sorted.first().snapshot
        val last = sorted.last().snapshot

        val months = sorted.map { i ->
            val t = i.snapshot.totals
            AnnualMonthRow(
                periodYm = i.snapshot.periodYm,
                label = DateFormats.monthYear(i.snapshot.periodYm.toYearMonth()),
                income = t.income, expense = t.expense, net = t.net, closing = t.closing,
                savingsRate = if (t.income > 0) t.net * 100.0 / t.income else null,
                isClosed = i.isClosed,
            )
        }
        val withData = sorted.filter { it.snapshot.totals.txCount > 0 }
        val dataMonths = withData.size.coerceAtLeast(1)
        val totalIncome = months.sumOf { it.income }
        val totalExpense = months.sumOf { it.expense }

        val periodLabel = if (sorted.size == 1) months.first().label else "${months.first().label} - ${months.last().label}"
        val combined = Checksum.sha256Hex(sorted.joinToString("|") { "${it.snapshot.periodYm}:${it.checksum}" })
        val docNumber = "DKU-${first.periodYm}-${last.periodYm}-" + if (sorted.size == 12) "ANN" else "RNG"
        val allClosed = sorted.all { it.isClosed }

        val meta = ReportMeta(
            title = title, ownerName = ownerName.ifBlank { "-" }, periodLabel = periodLabel,
            rangeLabel = "${DateFormats.dateLong(java.time.Instant.ofEpochMilli(first.startMillis).atZone(DateFormats.zone()).toLocalDate())} - " +
                DateFormats.dateLong(java.time.Instant.ofEpochMilli(last.endMillis).atZone(DateFormats.zone()).toLocalDate()),
            documentNumber = docNumber, printedAt = printedAt, isFinal = allClosed, revisionLabel = if (allClosed) "Final" else "Draft",
            currency = currency, walletScope = "Semua dompet", checksum = combined,
            startMillis = first.startMillis, endMillis = last.endMillis,
        )

        return AnnualReportModel(
            meta = meta,
            months = months,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            totalNet = totalIncome - totalExpense,
            openingBalance = first.totals.opening,
            closingBalance = last.totals.closing,
            avgMonthlyIncome = totalIncome / dataMonths,
            avgMonthlyExpense = totalExpense / dataMonths,
            savingsRate = if (totalIncome > 0) (totalIncome - totalExpense) * 100.0 / totalIncome else null,
            expenseByCategory = aggregate(sorted, income = false),
            incomeByCategory = aggregate(sorted, income = true),
            mostExpensiveMonth = months.filter { it.expense > 0 }.maxByOrNull { it.expense },
            thriftiestMonth = withData.map { w -> months.first { it.periodYm == w.snapshot.periodYm } }.filter { it.expense > 0 }.minByOrNull { it.expense },
            transactionCount = sorted.sumOf { it.snapshot.totals.txCount },
            allClosed = allClosed,
            closingInfo = ClosingInfo(userNote, docNumber, combined, ReportBuilder.qrPayload(docNumber, combined)),
        )
    }

    private fun aggregate(inputs: List<AnnualInput>, income: Boolean): List<CategoryAnalysisRow> {
        data class Acc(var name: String, var total: Long = 0, var count: Int = 0)
        val acc = HashMap<Long?, Acc>()
        for (i in inputs) {
            val names = i.snapshot.categories.associate { it.id to it.name }
            val rows: List<SnapCategoryTotal> = if (income) i.snapshot.incomeByCategory else i.snapshot.expenseByCategory
            for (r in rows) {
                val a = acc.getOrPut(r.categoryId) { Acc(r.categoryId?.let { names[it] } ?: "Tanpa kategori") }
                a.total += r.total
                a.count += r.count
            }
        }
        val grand = acc.values.sumOf { it.total }
        if (grand <= 0) return emptyList()
        val palette = Palette.colors
        return acc.entries.map { (id, a) ->
            CategoryAnalysisRow(
                name = a.name, count = a.count, total = a.total, percent = Math.round(a.total * 1000.0 / grand) / 10.0,
                average = if (a.count > 0) a.total / a.count else 0, previousTotal = 0,
                colorArgb = palette[(id ?: 0L).toInt().mod(palette.size)],
            )
        }.sortedByDescending { it.total }
    }
}

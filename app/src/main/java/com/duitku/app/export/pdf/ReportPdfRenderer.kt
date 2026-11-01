package com.duitku.app.export.pdf

import android.graphics.Color
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.BudgetStatus
import com.duitku.app.domain.model.CategoryAnalysisRow
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.ReportModel
import com.duitku.app.domain.model.ReportRow
import com.duitku.app.domain.model.ReportTx
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.usecase.statement.Checksum
import java.util.Locale

/** Menyusun PDF "Laporan Mutasi Keuangan Pribadi" (14 bagian) dari [ReportModel]. */
class ReportPdfRenderer(
    private val report: ReportModel,
    private val options: ExportOptions,
) {
    private val meta = report.meta
    private val cur = meta.currency
    private val style = PdfStyle.of(options.theme)
    private val paints = PdfPaints(style)
    private val composer = PageComposer(
        paints = paints,
        headerLeft = "DuitKu · ${meta.title}",
        headerRight = "${meta.periodLabel} · ${meta.documentNumber}",
        footerLeft = "Dokumen ini dibuat otomatis oleh DuitKu · ${meta.documentNumber} · ${meta.revisionLabel}",
    )
    private val charts = PdfCharts(composer)

    private fun money(v: Long) = MoneyFormatter.format(v, cur)
    private fun num(v: Long) = MoneyFormatter.format(v, cur, withSymbol = false)
    private fun pct(v: Double) = String.format(Locale.forLanguageTag("id-ID"), "%.1f", v) + "%"

    fun render(sink: PdfSink): Int {
        cover()
        executiveSummary()
        walletSummary()
        detail()
        categoryAnalysis("5. Analisis Pengeluaran Per Kategori", report.expenseAnalysis, expense = true)
        categoryAnalysis("6. Analisis Pemasukan Per Sumber", report.incomeAnalysis, expense = false)
        budget()
        trends()
        topTransactions()
        recurring()
        goals()
        debts()
        reconciliation()
        closingPage()
        composer.render(sink)
        return composer.pageCount
    }

    // 1. Sampul ---------------------------------------------------------------------------------
    private fun cover() {
        val c = composer
        // Logo: lingkaran berisi dompet sederhana.
        val logoX = c.left
        val logoY = c.y + 4f
        c.circle(logoX + 22f, logoY + 22f, 22f, style.accent)
        c.roundRect(logoX + 10f, logoY + 15f, logoX + 34f, logoY + 31f, 3f, if (style.useFills) Color.WHITE else Color.WHITE, Color.BLACK.takeIf { !style.useFills })
        c.rect(logoX + 25f, logoY + 20f, logoX + 34f, logoY + 26f, if (style.useFills) style.accent else Color.BLACK)
        c.text("DuitKu", logoX + 54f, logoY + 20f, paints.title)
        c.text("Pencatat keuangan pribadi", logoX + 54f, logoY + 35f, paints.subtitle)
        c.moveTo(logoY + 62f)

        c.text(meta.title, c.left, c.y + 18f, paints.big)
        c.line(c.left, c.y + 26f, c.right, c.y + 26f, style.accent, 1.5f)
        c.moveTo(c.y + 36f)

        val status = if (meta.isFinal) "FINAL (bulan tertutup)" else "DRAFT (bulan belum ditutup)"
        val fields = listOf(
            "Nama pemilik" to meta.ownerName,
            "Periode" to meta.periodLabel,
            "Rentang tanggal" to meta.rangeLabel,
            "Cakupan dompet" to meta.walletScope,
            "Nomor dokumen" to meta.documentNumber,
            "Tanggal cetak" to DateFormats.dateTime(meta.printedAt),
            "Status" to status,
            "Nomor revisi" to meta.revisionLabel,
            "Mata uang" to cur,
        )
        fields.forEachIndexed { i, (label, value) ->
            if (style.useFills && i % 2 == 0) c.rect(c.left, c.y, c.right, c.y + 16f, style.altRow)
            c.text(label, c.left + 6f, c.y + 11f, paints.small)
            val color = if (label == "Status") (if (meta.isFinal) style.income else style.warning) else null
            c.text(value, c.left + 130f, c.y + 11f, paints.with(paints.bodyBold, color))
            c.space(16f)
        }
        c.space(6f)
    }

    // 2. Ringkasan eksekutif --------------------------------------------------------------------
    private fun executiveSummary() {
        val s = report.summary
        composer.section("2. Ringkasan Eksekutif", keepWith = 140f)
        val items = listOf(
            "Saldo awal" to money(s.opening) to null,
            "Total pemasukan" to money(s.income) to style.income,
            "Total pengeluaran" to money(s.expense) to style.expense,
            "Transfer (masuk / keluar)" to "${num(s.transferIn)} / ${num(s.transferOut)}" to style.transfer,
            "Saldo akhir" to money(s.closing) to null,
            "Net cashflow" to money(s.net) to (if (s.net >= 0) style.income else style.expense),
            "Savings rate" to (s.savingsRate?.let { pct(it) } ?: "-") to null,
            "Rata-rata pengeluaran / hari" to money(s.avgDailyExpense) to null,
            "Hari paling boros" to (s.mostExpensiveDay?.let { "${DateFormats.dayName(it.first)}, ${DateFormats.dayMonth(it.first)} (${money(it.second)})" } ?: "-") to null,
            "Transaksi pengeluaran terbesar" to (s.biggestExpense?.let { "${money(it.amount)} - ${it.categoryLabel}" } ?: "-") to null,
            "Transaksi pemasukan terbesar" to (s.biggestIncome?.let { "${money(it.amount)} - ${it.categoryLabel}" } ?: "-") to null,
            "Pengeluaran vs bulan lalu" to comparisonText(s.expenseVsPrevious) to null,
            "Pemasukan vs bulan lalu" to comparisonText(s.incomeVsPrevious) to null,
        )
        if (s.added != 0L) {
            composer.paragraph("Termasuk saldo awal dompet baru ${money(s.added)} yang dibuat dalam periode ini.", paints.smallMuted)
        }
        items.forEachIndexed { i, (pair, color) ->
            composer.ensure(16f)
            val y = composer.y
            if (style.useFills && i % 2 == 0) composer.rect(composer.left, y, composer.right, y + 15f, style.altRow)
            composer.text(pair.first, composer.left + 6f, y + 10.5f, paints.small)
            composer.text(composer.fit(pair.second, paints.with(paints.bodyBold), composer.contentW - 190f), composer.right - 6f, y + 10.5f, paints.with(paints.smallBold, color), Align.RIGHT)
            composer.space(15f)
        }
        composer.space(6f)
        composer.text("Kesimpulan", composer.left, composer.y + 9f, paints.h2)
        composer.space(14f)
        composer.paragraph(s.conclusion, paints.body)
        composer.space(4f)
    }

    private fun comparisonText(c: com.duitku.app.domain.model.Comparison?): String {
        if (c == null) return "-"
        val p = c.percent ?: return "baru (bulan lalu 0)"
        return when {
            c.delta == 0L -> "sama (${money(c.previous)})"
            c.delta > 0 -> "naik ${pct(p)} (${money(c.previous)} -> ${money(c.current)})"
            else -> "turun ${pct(-p)} (${money(c.previous)} -> ${money(c.current)})"
        }
    }

    // 3. Saldo per dompet -----------------------------------------------------------------------
    private fun walletSummary() {
        composer.section("3. Ringkasan Saldo Per Dompet", keepWith = 60f)
        val cols = listOf(
            Col("Dompet", 90f), Col("Saldo awal", 62f, Align.RIGHT), Col("Total masuk", 62f, Align.RIGHT),
            Col("Total keluar", 62f, Align.RIGHT), Col("Transfer masuk", 62f, Align.RIGHT),
            Col("Transfer keluar", 62f, Align.RIGHT), Col("Saldo akhir", 66f, Align.RIGHT),
        )
        val rows = report.wallets.map {
            TableRow(listOf(cell(it.name + if (it.added != 0L) " *" else ""), cell(num(it.opening)), cell(num(it.income), style.income), cell(num(it.expense), style.expense), cell(num(it.transferIn)), cell(num(it.transferOut)), cell(num(it.closing), bold = true)))
        } + TableRow(
            listOf(
                cell("TOTAL"), cell(num(report.wallets.sumOf { it.opening })), cell(num(report.wallets.sumOf { it.income })),
                cell(num(report.wallets.sumOf { it.expense })), cell(num(report.wallets.sumOf { it.transferIn })),
                cell(num(report.wallets.sumOf { it.transferOut })), cell(num(report.wallets.sumOf { it.closing })),
            ),
            RowKind.TOTAL,
        )
        PdfTable(composer, cols).draw(rows)
        if (report.wallets.any { it.added != 0L }) {
            composer.paragraph("* Dompet dibuat dalam periode ini; saldo awal dompet ${money(report.wallets.sumOf { it.added })} dihitung sebagai penambahan.", paints.smallMuted)
        }
    }

    // 4. Rincian mutasi -------------------------------------------------------------------------
    private fun detail() {
        composer.section("4. Rincian Mutasi Lengkap", keepWith = 80f)
        if (report.days.isEmpty()) {
            composer.paragraph("Tidak ada transaksi pada periode ini.", paints.body)
            return
        }
        composer.paragraph(
            "Keterangan: angka dalam tanda kurung = transfer antar dompet (tidak mengubah total saldo); [B] = transaksi berulang; " +
                "Debit = uang keluar, Kredit = uang masuk.",
            paints.smallMuted,
        )
        composer.space(2f)
        val cols = listOf(
            Col("No", 20f, Align.RIGHT), Col("Tanggal & Jam", 56f), Col("Dompet", 50f, maxLines = 2),
            Col("Kategori > Subkategori", 82f, maxLines = 2), Col("Deskripsi / Catatan", 86f, maxLines = 2),
            Col("Tag", 38f, maxLines = 2), Col("Debit", 52f, Align.RIGHT), Col("Kredit", 52f, Align.RIGHT),
            Col("Saldo berjalan", 58f, Align.RIGHT), Col("ID", 26f, Align.RIGHT),
        )
        val rows = mutableListOf<TableRow>()
        for (day in report.days) {
            rows += TableRow(listOf(cell(DateFormats.dayHeader(day.date))), RowKind.DAY_HEADER, spanFirst = true)
            day.rows.forEach { rows += txRow(it) }
            rows += TableRow(
                listOf(cell(""), cell(""), cell(""), cell(""), cell("Subtotal harian", bold = true), cell(""), cell(num(day.totalDebit), style.expense, true), cell(num(day.totalCredit), style.income, true), cell(""), cell("")),
                RowKind.SUBTOTAL,
            )
        }
        PdfTable(composer, cols).draw(rows)
    }

    private fun txRow(r: ReportRow): TableRow {
        val tx = r.tx
        val isTransfer = tx.type == TransactionType.TRANSFER
        val category = if (isTransfer) "Transfer: ${tx.walletName} -> ${tx.toWalletName ?: "?"}" else tx.categoryLabel
        val note = tx.note + if (tx.recurring) " [B]" else ""
        val debit = when {
            r.neutralTransfer -> "(${num(tx.amount)})"
            r.debit > 0 -> num(r.debit)
            else -> ""
        }
        return TableRow(
            listOf(
                cell(r.number.toString()),
                cell(DateFormats.dayMonth(tx.at.toLocalDate()) + " " + DateFormats.time(tx.at)),
                cell(tx.walletName),
                cell(category),
                cell(note.trim()),
                cell(tx.tags.joinToString(" ") { "#$it" }),
                cell(debit, if (r.neutralTransfer) style.transfer else style.expense),
                cell(if (r.credit > 0) num(r.credit) else "", style.income),
                cell(num(r.balanceAfter)),
                cell(tx.id.toString(), style.muted),
            ),
        )
    }

    // 5 & 6. Analisis kategori ------------------------------------------------------------------
    private fun categoryAnalysis(title: String, rows: List<CategoryAnalysisRow>, expense: Boolean) {
        composer.section(title, keepWith = 80f)
        if (rows.isEmpty()) {
            composer.paragraph(if (expense) "Tidak ada pengeluaran pada periode ini." else "Tidak ada pemasukan pada periode ini.", paints.body)
            return
        }
        val cols = listOf(
            Col(if (expense) "Kategori" else "Sumber", 100f), Col("Jml trx", 36f, Align.RIGHT), Col("Total", 70f, Align.RIGHT),
            Col("Persen", 40f, Align.RIGHT), Col("Rata-rata", 62f, Align.RIGHT), Col("Vs bulan lalu", 80f, Align.RIGHT),
        )
        val tableRows = rows.map {
            val change = it.changePercent
            val changeText = when {
                change == null && it.previousTotal == 0L -> "baru"
                change == null -> "-"
                change > 0.05 -> "naik ${pct(change)}"
                change < -0.05 -> "turun ${pct(-change)}"
                else -> "sama"
            }
            val changeColor = when {
                change == null -> null
                (change > 0) == expense && Math.abs(change) > 0.05 -> style.expense
                Math.abs(change) > 0.05 -> style.income
                else -> null
            }
            TableRow(listOf(cell(it.name), cell(it.count.toString()), cell(num(it.total)), cell(pct(it.percent)), cell(num(it.average)), cell(changeText, changeColor)))
        } + TableRow(
            listOf(cell("TOTAL"), cell(rows.sumOf { it.count }.toString()), cell(num(rows.sumOf { it.total })), cell("100%"), cell(""), cell("")),
            RowKind.TOTAL,
        )
        PdfTable(composer, cols).draw(tableRows)

        if (options.includeCharts) {
            composer.space(2f)
            charts.caption(if (expense) "Komposisi pengeluaran" else "Komposisi pemasukan")
            val top = rows.take(7)
            val rest = rows.drop(7).sumOf { it.total }
            val slices = top.map { PdfSlice(it.name, it.total, it.colorArgb) } + if (rest > 0) listOf(PdfSlice("Lainnya", rest, Color.GRAY)) else emptyList()
            charts.donut(slices, currency = cur)
            composer.space(4f)
            charts.caption(if (expense) "Pengeluaran per kategori (terbesar)" else "Pemasukan per sumber")
            charts.bars(top.map { PdfBarGroup(composer.fit(it.name, paints.smallMuted, 56f), listOf(it.total)) }, listOf(if (expense) style.expense else style.income), height = 90f)
        }
    }

    // 7. Anggaran -------------------------------------------------------------------------------
    private fun budget() {
        composer.section("7. Realisasi Anggaran", keepWith = 80f)
        val b = report.budget
        if (b == null) {
            composer.paragraph("Tidak ada anggaran yang diatur untuk periode ini.", paints.body)
            return
        }
        val cols = listOf(
            Col("Kategori", 92f), Col("Anggaran", 62f, Align.RIGHT), Col("Sisa bln lalu", 58f, Align.RIGHT), Col("Terpakai", 62f, Align.RIGHT),
            Col("Sisa", 62f, Align.RIGHT), Col("Persen", 38f, Align.RIGHT), Col("Status", 52f, Align.CENTER),
        )
        val rows = b.lines.map {
            val color = when (it.status) {
                BudgetStatus.SAFE -> style.income
                BudgetStatus.WARNING -> style.warning
                BudgetStatus.EXCEEDED -> style.expense
            }
            TableRow(
                listOf(
                    cell(it.name, bold = it.isTotal), cell(num(it.amount)), cell(if (it.rollIn > 0) num(it.rollIn) else "-"), cell(num(it.spent)),
                    cell(num(it.remaining), if (it.remaining < 0) style.expense else null), cell("${it.percent}%"), cell(it.status.label, color, true),
                ),
                if (it.isTotal) RowKind.TOTAL else RowKind.NORMAL,
            )
        }
        PdfTable(composer, cols).draw(rows)
        if (b.rolloverNext.isNotEmpty()) {
            composer.paragraph(
                "Rollover ke bulan depan: " + b.rolloverNext.joinToString("; ") { "${it.first} ${money(it.second)}" } + ".",
                paints.small,
            )
        } else {
            composer.paragraph("Tidak ada sisa anggaran yang dibawa ke bulan depan.", paints.smallMuted)
        }
    }

    // 8. Grafik tren ----------------------------------------------------------------------------
    private fun trends() {
        if (!options.includeCharts) return
        val t = report.trends
        composer.section("8. Grafik Tren", keepWith = 130f)
        charts.caption("Cashflow harian (pemasukan - pengeluaran)")
        charts.signedBars(t.dailyCashflow.map { it.first.dayOfMonth.toString() to it.second }, height = 90f)
        composer.space(2f)
        charts.caption("Saldo harian")
        charts.line(t.dailyBalance.map { it.first.dayOfMonth.toString() to it.second }, style.accent, height = 90f)
        composer.space(2f)
        charts.caption("Pemasukan vs pengeluaran per minggu")
        charts.bars(t.weekly.map { PdfBarGroup(it.first, listOf(it.second, it.third)) }, listOf(style.income, style.expense), height = 90f, seriesNames = listOf("Pemasukan", "Pengeluaran"))
        composer.space(2f)
        charts.caption("Pengeluaran per hari dalam seminggu")
        charts.bars(t.weekdayExpense.map { PdfBarGroup(it.first, listOf(it.second)) }, listOf(style.expense), height = 80f)
    }

    // 9. Top 10 ---------------------------------------------------------------------------------
    private fun topTransactions() {
        composer.section("9. Top 10 Transaksi Terbesar", keepWith = 80f)
        fun table(title: String, list: List<ReportTx>, color: Int) {
            composer.text(title, composer.left, composer.y + 9f, paints.h2)
            composer.space(13f)
            if (list.isEmpty()) {
                composer.paragraph("Tidak ada data.", paints.small)
                return
            }
            val cols = listOf(Col("#", 18f, Align.RIGHT), Col("Tanggal", 58f), Col("Kategori", 100f, maxLines = 2), Col("Catatan", 130f, maxLines = 2), Col("Dompet", 60f), Col("Jumlah", 70f, Align.RIGHT))
            PdfTable(composer, cols).draw(
                list.mapIndexed { i, tx ->
                    TableRow(listOf(cell("${i + 1}"), cell(DateFormats.date(tx.at)), cell(tx.categoryLabel), cell(tx.note), cell(tx.walletName), cell(num(tx.amount), color, true)))
                },
            )
        }
        table("Pengeluaran terbesar", report.topExpense, style.expense)
        table("Pemasukan terbesar", report.topIncome, style.income)
    }

    // 10. Transaksi berulang --------------------------------------------------------------------
    private fun recurring() {
        composer.section("10. Transaksi Berulang & Langganan", keepWith = 60f)
        val r = report.recurring
        if (r.items.isEmpty()) {
            composer.paragraph("Tidak ada transaksi berulang.", paints.body)
            return
        }
        val cols = listOf(Col("Nama", 130f), Col("Jenis", 62f), Col("Frekuensi", 80f), Col("Nominal", 80f, Align.RIGHT), Col("Status", 60f, Align.CENTER))
        PdfTable(composer, cols).draw(
            r.items.map {
                val freq = if (it.interval <= 1) it.frequency.label else "${it.frequency.label} (x${it.interval})"
                TableRow(listOf(cell(it.title), cell(it.type.label), cell(freq), cell(num(it.amount), if (it.type == TransactionType.EXPENSE) style.expense else style.income), cell(if (it.active) "Aktif" else "Dijeda")))
            },
        )
        composer.paragraph("Perkiraan total beban pengeluaran berulang per bulan: ${money(r.monthlyBurden)}.", paints.bodyBold)
    }

    // 11. Target tabungan -----------------------------------------------------------------------
    private fun goals() {
        composer.section("11. Posisi Target Tabungan", keepWith = 50f)
        if (report.goals.isEmpty()) {
            composer.paragraph("Tidak ada target tabungan.", paints.body)
            return
        }
        val cols = listOf(Col("Target", 130f), Col("Terkumpul", 80f, Align.RIGHT), Col("Target dana", 80f, Align.RIGHT), Col("Progres", 50f, Align.RIGHT), Col("Tenggat", 80f, Align.RIGHT))
        PdfTable(composer, cols).draw(
            report.goals.map {
                val progress = if (it.target > 0) (it.saved * 100 / it.target).coerceIn(0, 100) else 0
                TableRow(listOf(cell(it.name), cell(num(it.saved)), cell(num(it.target)), cell("$progress%", if (progress >= 100) style.income else null), cell(it.deadline?.let { d -> DateFormats.date(d) } ?: "-")))
            },
        )
    }

    // 12. Utang piutang -------------------------------------------------------------------------
    private fun debts() {
        composer.section("12. Posisi Utang & Piutang", keepWith = 50f)
        if (report.debts.isEmpty()) {
            composer.paragraph("Tidak ada utang atau piutang aktif.", paints.body)
            return
        }
        val cols = listOf(Col("Pihak", 110f), Col("Jenis", 50f), Col("Pokok", 70f, Align.RIGHT), Col("Terbayar", 70f, Align.RIGHT), Col("Sisa", 70f, Align.RIGHT), Col("Jatuh tempo", 70f, Align.RIGHT))
        PdfTable(composer, cols).draw(
            report.debts.map {
                TableRow(listOf(cell(it.party), cell(it.kind.label), cell(num(it.principal)), cell(num(it.paid)), cell(num((it.principal - it.paid).coerceAtLeast(0)), bold = true), cell(it.due?.let { d -> DateFormats.date(d) } ?: "-")))
            },
        )
        val debt = report.debts.filter { it.kind == com.duitku.app.domain.model.DebtKind.DEBT }.sumOf { (it.principal - it.paid).coerceAtLeast(0) }
        val receivable = report.debts.filter { it.kind == com.duitku.app.domain.model.DebtKind.RECEIVABLE }.sumOf { (it.principal - it.paid).coerceAtLeast(0) }
        composer.paragraph("Total sisa utang ${money(debt)}; total sisa piutang ${money(receivable)}.", paints.bodyBold)
    }

    // 13. Rekonsiliasi --------------------------------------------------------------------------
    private fun reconciliation() {
        val r = report.reconciliation
        composer.section("13. Rekonsiliasi Saldo", keepWith = 120f)
        val lines = listOf(
            "Saldo awal" to money(r.opening),
            "+ Pemasukan" to money(r.income),
            "- Pengeluaran" to money(r.expense),
        ) + (if (r.added != 0L) listOf("+ Saldo awal dompet baru" to money(r.added)) else emptyList()) + listOf(
            "= Saldo akhir seharusnya" to money(r.expectedClosing),
            "Saldo akhir tercatat" to money(r.recordedClosing),
        )
        lines.forEach { (label, value) ->
            composer.ensure(14f)
            composer.text(label, composer.left + 6f, composer.y + 10f, paints.body)
            composer.text(value, composer.left + 230f, composer.y + 10f, paints.bodyBold, Align.RIGHT)
            composer.space(14f)
        }
        composer.ensure(24f)
        val ok = r.isBalanced
        val color = if (ok) style.income else style.expense
        val y = composer.y + 4f
        composer.roundRect(composer.left, y, composer.right, y + 18f, 4f, null, color)
        // Tanda centang / peringatan digambar dengan garis agar tidak bergantung pada font.
        if (ok) {
            composer.line(composer.left + 8f, y + 9f, composer.left + 12f, y + 13f, color, 2f)
            composer.line(composer.left + 12f, y + 13f, composer.left + 19f, y + 5f, color, 2f)
        } else {
            composer.line(composer.left + 8f, y + 5f, composer.left + 18f, y + 13f, color, 2f)
            composer.line(composer.left + 18f, y + 5f, composer.left + 8f, y + 13f, color, 2f)
        }
        composer.text(
            if (ok) "Saldo cocok: saldo awal + masuk - keluar = saldo akhir." else "PERINGATAN: selisih ${money(r.difference)} antara saldo seharusnya dan saldo tercatat.",
            composer.left + 26f, y + 12.5f, paints.with(paints.bodyBold, color),
        )
        composer.moveTo(y + 26f)

        composer.text("Pencocokan dengan saldo rekening bank", composer.left, composer.y + 9f, paints.h2)
        composer.space(14f)
        composer.paragraph("Isi kolom \"Saldo sesuai rekening bank\" untuk memeriksa selisih dengan catatan DuitKu.", paints.smallMuted)
        val cols = listOf(Col("Dompet", 110f), Col("Saldo tercatat", 80f, Align.RIGHT), Col("Saldo sesuai rekening bank", 110f, Align.RIGHT), Col("Selisih", 80f, Align.RIGHT), Col("Cek", 40f, Align.CENTER))
        PdfTable(composer, cols).draw(
            r.wallets.map { w ->
                val diff = w.bankDifference
                TableRow(
                    listOf(
                        cell(w.name), cell(num(w.recorded)),
                        cell(w.bankBalance?.let { num(it) } ?: "........................"),
                        cell(diff?.let { num(it) } ?: "", if (diff != null && diff != 0L) style.expense else null),
                        cell(if (diff == null) "" else if (diff == 0L) "OK" else "BEDA", if (diff == 0L) style.income else style.expense, true),
                    ),
                )
            },
        )
    }

    // 14. Penutup -------------------------------------------------------------------------------
    private fun closingPage() {
        composer.newPage()
        composer.section("14. Catatan & Verifikasi Dokumen", keepWith = 200f)
        composer.text("Catatan bulan", composer.left, composer.y + 9f, paints.h2)
        composer.space(14f)
        val note = report.closing.userNote.ifBlank { "(tidak ada catatan)" }
        composer.paragraph(note, paints.body)
        composer.space(8f)
        if (report.closing.userNote.isBlank()) {
            repeat(3) {
                composer.line(composer.left, composer.y + 14f, composer.right, composer.y + 14f, style.line, 0.5f)
                composer.space(16f)
            }
        }
        composer.space(10f)

        val top = composer.y
        val qrSize = 110f
        composer.ensure(qrSize + 30f)
        QrDrawer.draw(composer, report.closing.qrPayload, composer.right - qrSize - 4f, top + 4f, qrSize)
        composer.text("Nomor dokumen", composer.left, top + 10f, paints.smallMuted)
        composer.text(report.closing.documentNumber, composer.left, top + 24f, paints.big)
        composer.text("Checksum singkat (SHA-256)", composer.left, top + 44f, paints.smallMuted)
        composer.text(Checksum.short(report.closing.checksum), composer.left, top + 58f, paints.big)
        composer.text("Checksum lengkap", composer.left, top + 76f, paints.smallMuted)
        report.closing.checksum.chunked(32).forEachIndexed { i, part ->
            composer.text(part, composer.left, top + 88f + i * 10f, paints.cell)
        }
        composer.moveTo(top + qrSize + 18f)
        composer.paragraph(
            "Pindai kode QR untuk membaca nomor dokumen dan hash. Cocokkan dengan menu Arsip Mutasi > Verifikasi di aplikasi DuitKu " +
                "untuk memastikan isi laporan tidak berubah sejak bulan ditutup.",
            paints.small, width = composer.contentW - qrSize - 20f,
        )
        composer.space(20f)
        composer.ensure(30f)
        composer.line(composer.left, composer.y, composer.right, composer.y, style.line)
        composer.space(14f)
        composer.text("Dokumen ini dibuat otomatis oleh DuitKu.", composer.pageW / 2, composer.y, paints.bodyBold, Align.CENTER)
        composer.text(
            "Dicetak ${DateFormats.dateTime(meta.printedAt)} · ${if (meta.isFinal) "Final" else "Draft"} · ${meta.revisionLabel}",
            composer.pageW / 2, composer.y + 12f, paints.smallMuted, Align.CENTER,
        )
    }
}

package com.duitku.app.export.pdf

import android.graphics.Color
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.AnnualReportModel
import com.duitku.app.domain.model.PdfTheme
import com.duitku.app.domain.usecase.statement.Checksum
import java.util.Locale

/** PDF Laporan Tahunan / rentang bebas. */
class AnnualPdfRenderer(
    private val report: AnnualReportModel,
    theme: PdfTheme,
    private val includeCharts: Boolean,
) {
    private val meta = report.meta
    private val cur = meta.currency
    private val style = PdfStyle.of(theme)
    private val paints = PdfPaints(style)
    private val composer = PageComposer(
        paints, "DuitKu · ${meta.title}", "${meta.periodLabel} · ${meta.documentNumber}",
        "Dokumen ini dibuat otomatis oleh DuitKu · ${meta.documentNumber}",
    )
    private val charts = PdfCharts(composer)

    private fun money(v: Long) = MoneyFormatter.format(v, cur)
    private fun num(v: Long) = MoneyFormatter.format(v, cur, withSymbol = false)
    private fun pct(v: Double) = String.format(Locale.forLanguageTag("id-ID"), "%.1f", v) + "%"

    fun render(sink: PdfSink): Int {
        cover()
        summary()
        monthTable()
        if (includeCharts) monthCharts()
        categories("Pengeluaran Terbesar Per Kategori", report.expenseByCategory, expense = true)
        categories("Pemasukan Per Sumber", report.incomeByCategory, expense = false)
        closing()
        composer.render(sink)
        return composer.pageCount
    }

    private fun cover() {
        val c = composer
        c.circle(c.left + 22f, c.y + 26f, 22f, style.accent)
        c.text("DuitKu", c.left + 54f, c.y + 24f, paints.title)
        c.text("Pencatat keuangan pribadi", c.left + 54f, c.y + 39f, paints.subtitle)
        c.moveTo(c.y + 62f)
        c.text(meta.title, c.left, c.y + 18f, paints.big)
        c.line(c.left, c.y + 26f, c.right, c.y + 26f, style.accent, 1.5f)
        c.moveTo(c.y + 36f)
        listOf(
            "Nama pemilik" to meta.ownerName, "Periode" to meta.periodLabel, "Rentang tanggal" to meta.rangeLabel,
            "Nomor dokumen" to meta.documentNumber, "Tanggal cetak" to DateFormats.dateTime(meta.printedAt),
            "Status" to if (meta.isFinal) "FINAL (semua bulan tertutup)" else "DRAFT (ada bulan yang belum ditutup)",
            "Mata uang" to cur, "Jumlah transaksi" to report.transactionCount.toString(),
        ).forEachIndexed { i, (label, value) ->
            if (style.useFills && i % 2 == 0) c.rect(c.left, c.y, c.right, c.y + 16f, style.altRow)
            c.text(label, c.left + 6f, c.y + 11f, paints.small)
            c.text(value, c.left + 130f, c.y + 11f, paints.bodyBold)
            c.space(16f)
        }
        c.space(6f)
    }

    private fun summary() {
        composer.section("Ringkasan", keepWith = 120f)
        val r = report
        val months = r.months.filter { it.income > 0 || it.expense > 0 }.size.coerceAtLeast(1)
        listOf(
            "Saldo awal periode" to money(r.openingBalance) to null,
            "Total pemasukan" to money(r.totalIncome) to style.income,
            "Total pengeluaran" to money(r.totalExpense) to style.expense,
            "Selisih bersih" to money(r.totalNet) to (if (r.totalNet >= 0) style.income else style.expense),
            "Saldo akhir periode" to money(r.closingBalance) to null,
            "Rata-rata pemasukan per bulan" to money(r.avgMonthlyIncome) to null,
            "Rata-rata pengeluaran per bulan" to money(r.avgMonthlyExpense) to null,
            "Tingkat tabungan" to (r.savingsRate?.let { pct(it) } ?: "-") to null,
            "Kategori pengeluaran terbesar" to (r.expenseByCategory.firstOrNull()?.let { "${it.name} (${money(it.total)})" } ?: "-") to null,
            "Bulan terboros" to (r.mostExpensiveMonth?.let { "${it.label} (${money(it.expense)})" } ?: "-") to style.expense,
            "Bulan terhemat" to (r.thriftiestMonth?.let { "${it.label} (${money(it.expense)})" } ?: "-") to style.income,
        ).forEachIndexed { i, (pair, color) ->
            composer.ensure(16f)
            val y = composer.y
            if (style.useFills && i % 2 == 0) composer.rect(composer.left, y, composer.right, y + 15f, style.altRow)
            composer.text(pair.first, composer.left + 6f, y + 10.5f, paints.small)
            composer.text(composer.fit(pair.second, paints.smallBold, composer.contentW - 190f), composer.right - 6f, y + 10.5f, paints.with(paints.smallBold, color), Align.RIGHT)
            composer.space(15f)
        }
        composer.space(4f)
        composer.paragraph(
            "Selama ${r.months.size} bulan (data pada $months bulan), pemasukan ${money(r.totalIncome)} dan pengeluaran ${money(r.totalExpense)}; " +
                "selisih bersih ${money(r.totalNet)}" + (r.savingsRate?.let { " atau ${pct(it)} dari pemasukan." } ?: "."),
            paints.body,
        )
    }

    private fun monthTable() {
        composer.section("Ringkasan Per Bulan", keepWith = 80f)
        val cols = listOf(
            Col("Bulan", 100f), Col("Pemasukan", 80f, Align.RIGHT), Col("Pengeluaran", 80f, Align.RIGHT), Col("Selisih", 80f, Align.RIGHT),
            Col("Saldo akhir", 84f, Align.RIGHT), Col("Tabungan", 46f, Align.RIGHT), Col("Status", 46f, Align.CENTER),
        )
        val rows = report.months.map {
            TableRow(
                listOf(
                    cell(it.label), cell(num(it.income), style.income), cell(num(it.expense), style.expense),
                    cell(num(it.net), if (it.net >= 0) style.income else style.expense), cell(num(it.closing)),
                    cell(it.savingsRate?.let { s -> pct(s) } ?: "-"), cell(if (it.isClosed) "Tertutup" else "Draft"),
                ),
            )
        } + TableRow(
            listOf(cell("TOTAL"), cell(num(report.totalIncome)), cell(num(report.totalExpense)), cell(num(report.totalNet)), cell(num(report.closingBalance)), cell(report.savingsRate?.let { pct(it) } ?: "-"), cell("")),
            RowKind.TOTAL,
        )
        PdfTable(composer, cols).draw(rows)
    }

    private fun monthCharts() {
        composer.section("Grafik", keepWith = 130f)
        charts.caption("Pemasukan vs pengeluaran per bulan")
        charts.bars(
            report.months.map { PdfBarGroup(it.label.substringBefore(' ').take(3), listOf(it.income, it.expense)) },
            listOf(style.income, style.expense), height = 110f, seriesNames = listOf("Pemasukan", "Pengeluaran"),
        )
        composer.space(2f)
        charts.caption("Saldo akhir per bulan")
        charts.line(report.months.map { it.label.substringBefore(' ').take(3) to it.closing }, style.accent, height = 100f, labelEvery = 1)
    }

    private fun categories(title: String, rows: List<com.duitku.app.domain.model.CategoryAnalysisRow>, expense: Boolean) {
        composer.section(title, keepWith = 80f)
        if (rows.isEmpty()) {
            composer.paragraph("Tidak ada data.", paints.body)
            return
        }
        val cols = listOf(Col("Kategori", 130f), Col("Jml trx", 44f, Align.RIGHT), Col("Total", 84f, Align.RIGHT), Col("Persen", 50f, Align.RIGHT), Col("Rata-rata", 74f, Align.RIGHT))
        PdfTable(composer, cols).draw(
            rows.take(15).map { TableRow(listOf(cell(it.name), cell(it.count.toString()), cell(num(it.total)), cell(pct(it.percent)), cell(num(it.average)))) },
        )
        if (includeCharts) {
            val top = rows.take(7)
            val rest = rows.drop(7).sumOf { it.total }
            charts.donut(top.map { PdfSlice(it.name, it.total, it.colorArgb) } + if (rest > 0) listOf(PdfSlice("Lainnya", rest, Color.GRAY)) else emptyList(), currency = cur)
        }
        if (!expense) composer.space(2f)
    }

    private fun closing() {
        composer.newPage()
        composer.section("Catatan & Verifikasi Dokumen", keepWith = 200f)
        composer.paragraph(report.closingInfo.userNote.ifBlank { "(tidak ada catatan)" }, paints.body)
        composer.space(16f)
        val top = composer.y
        val qr = 110f
        QrDrawer.draw(composer, report.closingInfo.qrPayload, composer.right - qr - 4f, top + 4f, qr)
        composer.text("Nomor dokumen", composer.left, top + 10f, paints.smallMuted)
        composer.text(report.closingInfo.documentNumber, composer.left, top + 24f, paints.big)
        composer.text("Checksum gabungan (SHA-256)", composer.left, top + 44f, paints.smallMuted)
        composer.text(Checksum.short(report.closingInfo.checksum), composer.left, top + 58f, paints.big)
        report.closingInfo.checksum.chunked(32).forEachIndexed { i, part -> composer.text(part, composer.left, top + 76f + i * 10f, paints.cell) }
        composer.moveTo(top + qr + 24f)
        composer.line(composer.left, composer.y, composer.right, composer.y, style.line)
        composer.text("Dokumen ini dibuat otomatis oleh DuitKu.", composer.pageW / 2, composer.y + 16f, paints.bodyBold, Align.CENTER)
    }
}

package com.duitku.app.export

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.AnnualReportModel
import com.duitku.app.domain.model.CategoryAnalysisRow
import com.duitku.app.domain.model.ReportModel
import com.duitku.app.domain.model.ReportRow
import com.duitku.app.domain.model.TransactionType
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sel pada lembar kerja: teks atau angka (rupiah utuh). */
sealed interface XCell {
    data class Text(val value: String, val bold: Boolean = false) : XCell
    data class Num(val value: Long, val bold: Boolean = false) : XCell
}

data class XlsxSheet(val name: String, val rows: List<List<XCell>>)

private fun t(value: String, bold: Boolean = false) = XCell.Text(value, bold)
private fun n(value: Long, bold: Boolean = false) = XCell.Num(value, bold)

/** Menyusun data tabel (sheet) dari model laporan; dipakai CSV dan XLSX agar isinya sama. */
object TabularData {
    val detailHeader = listOf(
        "Periode", "No", "ID", "Tanggal", "Jam", "Dompet", "Dompet Tujuan", "Tipe", "Kategori", "Catatan", "Tag",
        "Debit", "Kredit", "Saldo Berjalan", "Berulang",
    )

    fun detailRows(models: List<ReportModel>): List<List<XCell>> {
        val rows = mutableListOf<List<XCell>>(detailHeader.map { t(it, true) })
        for (m in models) {
            for (day in m.days) for (r in day.rows) rows += detailRow(m.meta.periodLabel, r)
        }
        return rows
    }

    private fun detailRow(period: String, r: ReportRow): List<XCell> {
        val tx = r.tx
        return listOf(
            t(period), n(r.number.toLong()), n(tx.id), t(DateFormats.date(tx.at.toLocalDate())), t(DateFormats.time(tx.at)),
            t(tx.walletName), t(tx.toWalletName.orEmpty()), t(tx.type.label), t(tx.categoryLabel), t(tx.note), t(tx.tags.joinToString(" ") { "#$it" }),
            n(if (tx.type == TransactionType.TRANSFER && r.neutralTransfer) 0 else r.debit),
            n(if (tx.type == TransactionType.TRANSFER && r.neutralTransfer) 0 else r.credit),
            n(r.balanceAfter), t(if (tx.recurring) "Ya" else "Tidak"),
        )
    }

    fun categoryRows(title: String, rows: List<CategoryAnalysisRow>): List<List<XCell>> =
        listOf(listOf(t(title, true)), listOf("Kategori", "Jumlah transaksi", "Total", "Persen", "Rata-rata", "Bulan lalu").map { t(it, true) }) +
            rows.map { listOf(t(it.name), n(it.count.toLong()), n(it.total), t(String.format(java.util.Locale.ROOT, "%.1f%%", it.percent)), n(it.average), n(it.previousTotal)) }

    fun monthlySummary(m: ReportModel): List<List<XCell>> {
        val s = m.summary
        return listOf(
            listOf(t(m.meta.title, true)),
            listOf(t("Pemilik"), t(m.meta.ownerName)), listOf(t("Periode"), t(m.meta.periodLabel)),
            listOf(t("Nomor dokumen"), t(m.meta.documentNumber)), listOf(t("Status"), t(if (m.meta.isFinal) "Final" else "Draft")),
            listOf(t("Cakupan"), t(m.meta.walletScope)),
            listOf(t("Saldo awal"), n(s.opening)), listOf(t("Pemasukan"), n(s.income)), listOf(t("Pengeluaran"), n(s.expense)),
            listOf(t("Saldo akhir"), n(s.closing)), listOf(t("Net cashflow"), n(s.net)),
            listOf(t("Rata-rata pengeluaran per hari"), n(s.avgDailyExpense)),
            listOf(t("Checksum"), t(m.meta.checksum)),
        )
    }

    fun walletRows(m: ReportModel): List<List<XCell>> =
        listOf(listOf("Dompet", "Saldo awal", "Masuk", "Keluar", "Transfer masuk", "Transfer keluar", "Saldo akhir").map { t(it, true) }) +
            m.wallets.map { listOf(t(it.name), n(it.opening), n(it.income), n(it.expense), n(it.transferIn), n(it.transferOut), n(it.closing)) }

    fun annualMonthRows(a: AnnualReportModel): List<List<XCell>> =
        listOf(listOf("Bulan", "Pemasukan", "Pengeluaran", "Selisih", "Saldo akhir", "Tabungan %", "Status").map { t(it, true) }) +
            a.months.map {
                listOf(
                    t(it.label), n(it.income), n(it.expense), n(it.net), n(it.closing),
                    t(it.savingsRate?.let { s -> String.format(java.util.Locale.ROOT, "%.1f", s) } ?: "-"), t(if (it.isClosed) "Tertutup" else "Draft"),
                )
            } + listOf(listOf(t("TOTAL", true), n(a.totalIncome, true), n(a.totalExpense, true), n(a.totalNet, true), n(a.closingBalance, true)))
}

object CsvWriter {
    private const val BOM = "\uFEFF"

    fun escape(value: String): String {
        // Cegah injeksi rumus saat dibuka di Excel/Sheets: awali dengan apostrof bila diawali = + - @.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@" && value.toLongOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun toCsv(rows: List<List<XCell>>): String = buildString {
        append(BOM)
        for (row in rows) {
            append(row.joinToString(",") { c -> if (c is XCell.Num) c.value.toString() else escape((c as XCell.Text).value) })
            append("\r\n")
        }
    }

    fun monthly(models: List<ReportModel>): String = toCsv(TabularData.detailRows(models))
}

/** Penulis .xlsx minimal (Office Open XML) tanpa pustaka tambahan: teks inline, angka, dan gaya tebal. */
object XlsxWriter {
    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "Minimal satu lembar kerja" }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> entry("xl/worksheets/sheet${i + 1}.xml", sheet(s)) }
        }
    }

    private fun sheet(s: XlsxSheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val columns = s.rows.maxOfOrNull { it.size } ?: 0
        if (columns > 0) {
            append("<cols>")
            for (c in 0 until columns) {
                val longest = s.rows.maxOf { row -> (row.getOrNull(c) as? XCell.Text)?.value?.length ?: 12 }
                append("""<col min="${c + 1}" max="${c + 1}" width="${(longest + 2).coerceIn(10, 48)}" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = columnName(c) + (r + 1)
                when (cell) {
                    is XCell.Text -> append("""<c r="$ref" t="inlineStr" s="${if (cell.bold) 1 else 0}"><is><t xml:space="preserve">${xml(cell.value)}</t></is></c>""")
                    is XCell.Num -> append("""<c r="$ref" s="${if (cell.bold) 3 else 2}"><v>${cell.value}</v></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    /** Nama lembar: maksimal 31 karakter dan tanpa \ / ? * [ ] : */
    fun safeName(name: String): String = name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifEmpty { "Sheet" }

    private fun xml(value: String): String = buildString {
        for (ch in value) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' -> Unit // karakter kontrol tidak valid di XML 1.0
                else -> append(ch)
            }
        }
    }

    private fun contentTypes(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(sheets: List<XlsxSheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${xml(safeName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="3" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs></styleSheet>"""

    fun monthlySheets(m: ReportModel): List<XlsxSheet> = listOf(
        XlsxSheet("Ringkasan", TabularData.monthlySummary(m)),
        XlsxSheet("Dompet", TabularData.walletRows(m)),
        XlsxSheet("Mutasi", TabularData.detailRows(listOf(m))),
        XlsxSheet("Kategori", TabularData.categoryRows("Pengeluaran per kategori", m.expenseAnalysis) + listOf(emptyList()) +
            TabularData.categoryRows("Pemasukan per sumber", m.incomeAnalysis)),
    )

    fun rangeSheets(a: AnnualReportModel, months: List<ReportModel>): List<XlsxSheet> = listOf(
        XlsxSheet("Per Bulan", TabularData.annualMonthRows(a)),
        XlsxSheet("Kategori", TabularData.categoryRows("Pengeluaran per kategori", a.expenseByCategory) + listOf(emptyList()) +
            TabularData.categoryRows("Pemasukan per sumber", a.incomeByCategory)),
        XlsxSheet("Mutasi", TabularData.detailRows(months)),
    )
}

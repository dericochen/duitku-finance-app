package com.duitku.app.export

import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.SnapCategory
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.usecase.report.DocumentInfo
import com.duitku.app.domain.usecase.report.ReportBuilder
import com.duitku.app.domain.usecase.statement.PeriodMath
import com.duitku.app.domain.usecase.statement.SnapshotFactory
import com.duitku.app.domain.usecase.statement.SnapshotInput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatsTest {

    private fun at(day: Int, hour: Int = 10) = LocalDateTime.of(2026, 10, day, hour, 0).toEpochMillis()
    private val start = at(1, 0)
    private val end = LocalDateTime.of(2026, 10, 31, 23, 59).toEpochMillis()

    private val walletA = Wallet(1, "Bank", WalletType.BANK, 1_000_000, "bank", 0, openedAt = 0)
    private val walletB = Wallet(2, "Tunai", WalletType.CASH, 0, "cash", 0, openedAt = 0)
    private val food = Category(10, "Makanan", CategoryKind.EXPENSE, null, "food", 0)
    private val coffee = Category(11, "Kopi", CategoryKind.EXPENSE, 10, "coffee", 0)
    private val salary = Category(12, "Gaji", CategoryKind.INCOME, null, "salary", 0)

    private val txs = listOf(
        Transaction(1, TransactionType.INCOME, 5_000_000, 1, categoryId = 12, occurredAt = at(2), note = "Gaji, bulan ini"),
        Transaction(2, TransactionType.EXPENSE, 200_000, 2, categoryId = 10, subCategoryId = 11, occurredAt = at(3), note = "=HYPERLINK(\"x\")", recurringId = 9),
        Transaction(3, TransactionType.TRANSFER, 300_000, 1, toWalletId = 2, occurredAt = at(4)),
        Transaction(4, TransactionType.EXPENSE, 100_000, 1, categoryId = 10, occurredAt = at(4, 12), tags = listOf("kantor")),
    )

    private fun report(walletId: Long? = null, bank: Map<Long, Long> = emptyMap()): com.duitku.app.domain.model.ReportModel {
        val before = mapOf(1L to 1_000_000L, 2L to 0L)
        val wallets = listOf(walletA, walletB)
        val closing = mapOf(1L to 1_000_000L + 5_000_000 - 300_000 - 100_000, 2L to 300_000L - 200_000)
        val flows = PeriodMath.walletFlows(wallets, before, txs, start, end, closing)
        val snapshot = SnapshotFactory.build(
            SnapshotInput(202610, start, end, 1, "IDR", "Dewi", flows, listOf(food, coffee, salary), txs),
        )
        return ReportBuilder.build(
            snapshot, ExportOptions(fromPeriodYm = 202610, toPeriodYm = 202610, walletId = walletId, bankBalances = bank),
            DocumentInfo("DKU-202610-0001", true, "Rev.1", "abc123", "catatan", 0L), today = LocalDate.of(2026, 11, 5),
        )
    }

    @Test
    fun `semua dompet - transfer netral dan saldo berjalan benar`() {
        val r = report()
        val rows = r.days.flatMap { it.rows }
        assertEquals(4, rows.size)
        val transfer = rows.first { it.tx.type == TransactionType.TRANSFER }
        assertTrue(transfer.neutralTransfer)
        assertEquals(0, transfer.debit + transfer.credit)
        assertEquals(listOf(6_000_000L, 5_800_000L, 5_800_000L, 5_700_000L), rows.map { it.balanceAfter })
        assertEquals(r.summary.closing, rows.last().balanceAfter)
        assertTrue(r.reconciliation.isBalanced)
        assertEquals("Makanan > Kopi", rows[1].tx.categoryLabel)
        assertTrue(rows[1].tx.recurring)
    }

    @Test
    fun `satu dompet - transfer menjadi debit atau kredit`() {
        val bank = report(walletId = 1)
        val rows = bank.days.flatMap { it.rows }
        assertEquals(3, rows.size) // tanpa pengeluaran tunai
        val transfer = rows.first { it.tx.type == TransactionType.TRANSFER }
        assertEquals(300_000, transfer.debit)
        assertFalse(transfer.neutralTransfer)
        assertEquals(5_600_000, rows.last().balanceAfter)

        val cash = report(walletId = 2).days.flatMap { it.rows }
        assertEquals(300_000, cash.first { it.tx.type == TransactionType.TRANSFER }.credit)
        assertEquals(100_000, cash.last().balanceAfter)
        assertEquals("Tunai", report(walletId = 2).meta.walletScope)
    }

    @Test
    fun `rekonsiliasi bank menghitung selisih`() {
        val r = report(bank = mapOf(1L to 5_550_000L)) // tercatat 5.600.000, bank 5.550.000
        val bankRow = r.reconciliation.wallets.first { it.name == "Bank" }
        assertEquals(50_000L, bankRow.bankDifference)
        assertEquals(null, r.reconciliation.wallets.first { it.name == "Tunai" }.bankDifference)
    }

    @Test
    fun `kesimpulan otomatis berbahasa Indonesia memuat angka utama`() {
        val s = report().summary
        assertTrue(s.conclusion.contains("pemasukan tercatat Rp 5.000.000"))
        assertTrue(s.conclusion.contains("pengeluaran Rp 300.000"))
        assertTrue(s.conclusion.contains("Arus kas bersih positif"))
        assertEquals(94.0, s.savingsRate!!, 0.01)
        assertEquals(200_000, s.biggestExpense!!.amount)
        assertEquals(5_000_000, s.biggestIncome!!.amount)
    }

    @Test
    fun `CSV memakai BOM, meng-quote koma dan menetralkan rumus`() {
        val csv = CsvWriter.monthly(listOf(report()))
        assertTrue(csv.startsWith("\uFEFFPeriode,No,ID,Tanggal"))
        assertTrue(csv.contains("\"Gaji, bulan ini\""))
        assertTrue("rumus harus dinetralkan", csv.contains("'=HYPERLINK"))
        assertEquals(5, csv.trim().lines().size)
        assertEquals("a", CsvWriter.escape("a"))
        assertEquals("\"a\"\"b\"", CsvWriter.escape("a\"b"))
        assertEquals("-5", CsvWriter.escape("-5")) // angka negatif tidak diberi apostrof
    }

    @Test
    fun `XLSX adalah zip valid berisi lembar kerja dan karakter khusus aman`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(XlsxWriter.monthlySheets(report()), out)
        val files = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { files[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet4.xml")))
        assertTrue(files.getValue("xl/workbook.xml").contains("name=\"Mutasi\""))
        val mutasi = files.getValue("xl/worksheets/sheet3.xml")
        assertTrue(mutasi.contains("&quot;x&quot;")) // tanda kutip di catatan di-escape
        assertTrue(mutasi.contains("<v>5000000</v>"))
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("Sheet", XlsxWriter.safeName("[]:*"))
        assertEquals(31, XlsxWriter.safeName("x".repeat(50)).length)
    }

    @Test
    fun `kategori induk dan subkategori di snapshot`() {
        val snapshot = SnapshotFactory.decode(
            SnapshotFactory.encode(
                SnapshotFactory.build(SnapshotInput(202610, start, end, 1, "IDR", "", PeriodMath.walletFlows(listOf(walletA), mapOf(1L to 0L), emptyList(), start, end), listOf(food, coffee), emptyList())),
            ),
        )
        assertEquals(listOf(SnapCategory(10, "Makanan", CategoryKind.EXPENSE), SnapCategory(11, "Kopi", CategoryKind.EXPENSE, 10)), snapshot.categories)
    }
}

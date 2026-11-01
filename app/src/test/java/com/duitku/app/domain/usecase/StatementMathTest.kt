package com.duitku.app.domain.usecase

import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.model.BudgetStatus
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.usecase.budget.BudgetEngine
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.domain.usecase.statement.DocumentNumber
import com.duitku.app.domain.usecase.statement.LiveMonthBuilder
import com.duitku.app.domain.usecase.statement.PeriodMath
import com.duitku.app.domain.usecase.statement.Reconciliation
import com.duitku.app.domain.usecase.statement.RunningBalance
import com.duitku.app.domain.usecase.statement.SnapshotFactory
import com.duitku.app.domain.usecase.statement.SnapshotInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementMathTest {

    private val start = 1_000L
    private val end = 9_999L

    private val walletA = Wallet(1, "A", WalletType.BANK, 1_000_000, "bank", 0, openedAt = 0)
    private val walletB = Wallet(2, "B", WalletType.CASH, 0, "cash", 0, openedAt = 0)
    private val food = Category(10, "Makanan", CategoryKind.EXPENSE, null, "food", 0)
    private val salary = Category(11, "Gaji", CategoryKind.INCOME, null, "salary", 0)

    private fun tx(id: Long, type: TransactionType, amount: Long, wallet: Long, at: Long, to: Long? = null, cat: Long? = null) =
        Transaction(id = id, type = type, amount = amount, walletId = wallet, toWalletId = to, categoryId = cat, occurredAt = at)

    private val txs = listOf(
        tx(1, TransactionType.INCOME, 500_000, 1, 2_000, cat = 11),
        tx(2, TransactionType.EXPENSE, 200_000, 1, 3_000, cat = 10),
        tx(3, TransactionType.TRANSFER, 300_000, 1, 4_000, to = 2),
        tx(4, TransactionType.EXPENSE, 50_000, 2, 5_000, cat = 10),
    )
    private val before = mapOf(1L to 1_000_000L, 2L to 0L)

    @Test
    fun `arus dan saldo akhir per dompet`() {
        val flows = PeriodMath.walletFlows(listOf(walletA, walletB), before, txs, start, end).associateBy { it.id }
        val a = flows.getValue(1)
        assertEquals(1_000_000, a.opening)
        assertEquals(500_000, a.income)
        assertEquals(200_000, a.expense)
        assertEquals(300_000, a.transferOut)
        assertEquals(1_000_000, a.closing)
        val b = flows.getValue(2)
        assertEquals(300_000, b.transferIn)
        assertEquals(50_000, b.expense)
        assertEquals(250_000, b.closing)
    }

    @Test
    fun `total dan rekonsiliasi seluruh dompet seimbang`() {
        val flows = PeriodMath.walletFlows(listOf(walletA, walletB), before, txs, start, end)
        val totals = PeriodMath.totals(txs, flows)
        assertEquals(1_000_000, totals.opening)
        assertEquals(500_000, totals.income)
        assertEquals(250_000, totals.expense)
        assertEquals(300_000, totals.transfer)
        assertEquals(1_250_000, totals.closing)
        assertEquals(250_000, totals.net)
        assertEquals(flows.sumOf { it.closing }, totals.closing)
        assertTrue(Reconciliation.check(totals).isBalanced)
    }

    @Test
    fun `dompet baru dalam periode dihitung sebagai arus tersendiri`() {
        val newWallet = Wallet(3, "Baru", WalletType.SAVINGS, 700_000, "savings", 0, openedAt = 6_000)
        val flows = PeriodMath.walletFlows(listOf(walletA, walletB, newWallet), before + (3L to 0L), txs, start, end)
        val totals = PeriodMath.totals(txs, flows)
        assertEquals(700_000, totals.added)
        assertEquals(1_950_000, totals.closing)
        assertTrue(Reconciliation.check(totals).isBalanced)
        // Dompet yang dibuka setelah periode tidak berpengaruh.
        val later = newWallet.copy(openedAt = end + 1)
        assertEquals(0, PeriodMath.walletFlows(listOf(later), mapOf(3L to 0L), emptyList(), start, end).single().added)
    }

    @Test
    fun `selisih rekonsiliasi terdeteksi`() {
        val r = Reconciliation.check(opening = 1_000, income = 500, expense = 200, added = 0, closing = 1_350)
        assertEquals(1_300, r.expectedClosing)
        assertEquals(50, r.difference)
        assertTrue(!r.isBalanced)
    }

    @Test
    fun `selisih terhadap saldo rekening bank`() {
        val r = Reconciliation.againstBank(appBalance = 1_250_000, bankBalance = 1_200_000)
        assertEquals(50_000, r.difference)
        assertTrue(!r.isMatch)
        assertTrue(Reconciliation.againstBank(10, 10).isMatch)
    }

    @Test
    fun `saldo berjalan semua dompet mengabaikan transfer`() {
        val rows = RunningBalance.compute(1_000_000, txs, walletId = null)
        assertEquals(listOf(1_500_000L, 1_300_000L, 1_300_000L, 1_250_000L), rows.map { it.balanceAfter })
    }

    @Test
    fun `saldo berjalan satu dompet memuat transfer masuk dan keluar saja yang terkait`() {
        val a = RunningBalance.compute(1_000_000, txs, walletId = 1)
        assertEquals(listOf(1_500_000L, 1_300_000L, 1_000_000L), a.map { it.balanceAfter })
        val b = RunningBalance.compute(0, txs, walletId = 2)
        assertEquals(listOf(300_000L, 250_000L), b.map { it.balanceAfter })
    }

    @Test
    fun `live month menggabungkan ringkasan dan saldo berjalan terbaru dulu`() {
        val month = LiveMonthBuilder.build(202611, start, end, null, txs, listOf(walletA, walletB), before, listOf(food, salary))
        assertEquals(1_250_000, month.summary.closing)
        assertEquals(4, month.rows.size)
        assertEquals(1_250_000, month.rows.first().balanceAfter) // terbaru dulu
        assertEquals(4L, month.rows.first().item.transaction.id)
        assertTrue(month.reconciliation.isBalanced)
        assertEquals(month.summary.closing, month.rows.first().balanceAfter)

        val single = LiveMonthBuilder.build(202611, start, end, 2, txs, listOf(walletA, walletB), before, listOf(food, salary))
        assertEquals(2, single.rows.size)
        assertEquals(250_000, single.summary.closing)
        assertTrue(single.reconciliation.isBalanced)
    }

    @Test
    fun `sha256 sesuai vektor uji standar`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksum.sha256Hex("abc"))
        assertEquals("BA7816BF8F01", Checksum.short(Checksum.sha256Hex("abc")))
    }

    @Test
    fun `format nomor dokumen`() {
        assertEquals("DKU-202611-0001", DocumentNumber.format(202611, 1))
        assertEquals("DKU-202601-0012", DocumentNumber.format(202601, 12))
    }

    private fun snapshotInput(list: List<Transaction> = txs): SnapshotInput {
        val flows = PeriodMath.walletFlows(listOf(walletA, walletB), before, list, start, end)
        return SnapshotInput(
            periodYm = 202611, startMillis = start, endMillis = end, startDay = 1, currency = "IDR", owner = "Dewi",
            wallets = flows, categories = listOf(food, salary), transactions = list,
        )
    }

    @Test
    fun `snapshot dapat diserialisasi bolak-balik tanpa kehilangan data`() {
        val snapshot = SnapshotFactory.build(snapshotInput())
        val decoded = SnapshotFactory.decode(SnapshotFactory.encode(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(4, decoded.transactions.size)
        assertEquals(250_000, decoded.expenseByCategory.single().total)
        assertEquals(500_000, decoded.incomeByCategory.single().total)
    }

    @Test
    fun `checksum snapshot stabil untuk data sama dan berubah bila data berubah`() {
        val json1 = SnapshotFactory.encode(SnapshotFactory.build(snapshotInput()))
        val json2 = SnapshotFactory.encode(SnapshotFactory.build(snapshotInput()))
        assertEquals(Checksum.sha256Hex(json1), Checksum.sha256Hex(json2))
        val changed = txs.map { if (it.id == 4L) it.copy(amount = 60_000) else it }
        val json3 = SnapshotFactory.encode(SnapshotFactory.build(snapshotInput(changed)))
        assertNotEquals(Checksum.sha256Hex(json1), Checksum.sha256Hex(json3))
    }

    // ---------- Anggaran ----------

    private fun budget(period: Int, cat: Long?, amount: Long, rollover: Boolean = false) =
        Budget(periodYm = period, categoryId = cat, amount = amount, rollover = rollover)

    @Test
    fun `status anggaran aman waspada melebihi`() {
        fun line(spent: Long) = BudgetLine(budget(202611, 10, 1_000), rollIn = 0, spent = spent)
        assertEquals(BudgetStatus.SAFE, line(799).status)
        assertEquals(BudgetStatus.WARNING, line(800).status)
        assertEquals(BudgetStatus.WARNING, line(999).status)
        assertEquals(BudgetStatus.EXCEEDED, line(1_000).status)
        assertEquals(BudgetStatus.EXCEEDED, line(1_500).status)
        assertEquals(150, line(1_500).percent)
        assertEquals(-500, line(1_500).remaining)
    }

    @Test
    fun `rollover membawa sisa bulan lalu hanya bila diaktifkan`() {
        val budgets = mapOf(
            202610 to listOf(budget(202610, 10, 1_000, rollover = true)),
            202611 to listOf(budget(202611, 10, 1_000)),
        )
        val spent = mapOf(202610 to mapOf<Long?, Long>(10L to 400L), 202611 to mapOf<Long?, Long>(10L to 1_200L))
        val line = BudgetEngine.lines(202611, budgets, spent).single()
        assertEquals(600, line.rollIn)
        assertEquals(1_600, line.limit)
        assertEquals(400, line.remaining)

        val noRoll = budgets + (202610 to listOf(budget(202610, 10, 1_000, rollover = false)))
        assertEquals(0, BudgetEngine.lines(202611, noRoll, spent).single().rollIn)
    }

    @Test
    fun `rollover tidak negatif dan berantai antar bulan`() {
        val budgets = mapOf(
            202609 to listOf(budget(202609, 10, 1_000, true)),
            202610 to listOf(budget(202610, 10, 1_000, true)),
            202611 to listOf(budget(202611, 10, 1_000)),
        )
        val spent = mapOf(
            202609 to mapOf<Long?, Long>(10L to 500L),   // sisa 500
            202610 to mapOf<Long?, Long>(10L to 3_000L), // 1000 + 500 - 3000 < 0 -> 0
        )
        assertEquals(0, BudgetEngine.lines(202611, budgets, spent).single().rollIn)
        val spent2 = spent + (202610 to mapOf<Long?, Long>(10L to 700L)) // 1000 + 500 - 700 = 800
        assertEquals(800, BudgetEngine.lines(202611, budgets, spent2).single().rollIn)
    }

    @Test
    fun `anggaran total memakai kunci null`() {
        val budgets = mapOf(202611 to listOf(budget(202611, null, 5_000)))
        val spent = mapOf(202611 to mapOf<Long?, Long>(null to 4_000L))
        val line = BudgetEngine.lines(202611, budgets, spent).single()
        assertTrue(line.isTotal)
        assertEquals(80, line.percent)
        assertEquals(BudgetStatus.WARNING, line.status)
    }
}

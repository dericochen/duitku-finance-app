package com.duitku.app.domain.usecase

import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.Comparison
import com.duitku.app.domain.model.StatsMode
import com.duitku.app.domain.model.StatsRange
import com.duitku.app.domain.model.StatsRequest
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.usecase.stats.StatsCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `rentang mingguan dimulai hari Senin`() {
        // 2026-11-04 adalah hari Rabu.
        val r = StatsCalculator.resolveRange(StatsRequest(StatsMode.WEEK, LocalDate.of(2026, 11, 4)), 1, today)
        assertEquals(LocalDate.of(2026, 11, 2), r.start)
        assertEquals(LocalDate.of(2026, 11, 8), r.endInclusive)
    }

    @Test
    fun `rentang bulanan mengikuti tanggal awal bulan`() {
        val r = StatsCalculator.resolveRange(StatsRequest(StatsMode.MONTH, LocalDate.of(2026, 11, 10)), 25, today)
        assertEquals(LocalDate.of(2026, 10, 25), r.start)
        assertEquals(LocalDate.of(2026, 11, 24), r.endInclusive)
        val next = StatsCalculator.resolveRange(StatsRequest(StatsMode.MONTH, LocalDate.of(2026, 11, 26)), 25, today)
        assertEquals(LocalDate.of(2026, 11, 25), next.start)
    }

    @Test
    fun `rentang tahunan dan custom yang terbalik dirapikan`() {
        val y = StatsCalculator.resolveRange(StatsRequest(StatsMode.YEAR, LocalDate.of(2026, 6, 1)), 1, today)
        assertEquals(LocalDate.of(2026, 1, 1), y.start)
        assertEquals(LocalDate.of(2026, 12, 31), y.endInclusive)
        val c = StatsCalculator.resolveRange(
            StatsRequest(StatsMode.CUSTOM, today, customFrom = LocalDate.of(2026, 11, 10), customTo = LocalDate.of(2026, 11, 1)), 1, today,
        )
        assertEquals(LocalDate.of(2026, 11, 1), c.start)
        assertEquals(LocalDate.of(2026, 11, 10), c.endInclusive)
    }

    @Test
    fun `mode 12 bulan terakhir mencakup tepat 12 periode berakhir di periode berjalan`() {
        val r = StatsCalculator.resolveRange(StatsRequest(StatsMode.ROLLING_12), 1, today)
        assertEquals(LocalDate.of(2025, 12, 1), r.start)
        assertEquals(LocalDate.of(2026, 11, 30), r.endInclusive)
    }

    @Test
    fun `rentang pembanding sama panjang tepat sebelumnya`() {
        val req = StatsRequest(StatsMode.MONTH, LocalDate.of(2026, 11, 10))
        val range = StatsCalculator.resolveRange(req, 1, today)
        val prev = StatsCalculator.previousRange(req, range, 1)
        assertEquals(LocalDate.of(2026, 10, 1), prev.start)
        assertEquals(LocalDate.of(2026, 10, 31), prev.endInclusive)

        val customReq = StatsRequest(StatsMode.CUSTOM, today, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))
        val customPrev = StatsCalculator.previousRange(customReq, StatsCalculator.resolveRange(customReq, 1, today), 1)
        assertEquals(LocalDate.of(2026, 10, 22), customPrev.start)
        assertEquals(LocalDate.of(2026, 10, 31), customPrev.endInclusive)
    }

    @Test
    fun `perbandingan persen naik turun dan pembanding nol`() {
        assertEquals(25.0, Comparison(125, 100).percent!!, 0.001)
        assertEquals(-50.0, Comparison(50, 100).percent!!, 0.001)
        assertNull(Comparison(50, 0).percent)
        assertEquals(0.0, Comparison(100, 100).percent!!, 0.001)
    }

    private val cats = listOf(
        Category(1, "Makanan", CategoryKind.EXPENSE, null, "food", 111),
        Category(2, "Transport", CategoryKind.EXPENSE, null, "transport", 222),
    )

    @Test
    fun `statistik kategori terurut dan persentase berjumlah 100`() {
        val stats = StatsCalculator.categoryStats(
            totals = mapOf(1L to (600L to 6), 2L to (300L to 3), null to (100L to 1)),
            categories = cats,
            previousTotals = mapOf(1L to 500L),
        )
        assertEquals(listOf("Makanan", "Transport", "Tanpa kategori"), stats.map { it.name })
        assertEquals(60.0, stats[0].percent, 0.001)
        assertEquals(100.0, stats.sumOf { it.percent }, 0.2)
        assertEquals(500, stats[0].previousTotal)
        assertEquals(111, stats[0].colorArgb)
        assertTrue(StatsCalculator.categoryStats(emptyMap(), cats).isEmpty())
    }

    private fun tx(type: TransactionType, amount: Long, day: Int) = Transaction(
        type = type, amount = amount, walletId = 1,
        occurredAt = LocalDateTime.of(2026, 11, day, 10, 0).toEpochMillis(),
    )

    @Test
    fun `tren saldo harian memakai saldo awal dan mengabaikan transfer`() {
        val range = StatsRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), "x")
        val txs = listOf(
            tx(TransactionType.INCOME, 1_000, 2),
            tx(TransactionType.EXPENSE, 300, 3),
            tx(TransactionType.TRANSFER, 9_999, 3),
        )
        val trend = StatsCalculator.balanceTrend(5_000, txs, range, today = LocalDate.of(2026, 11, 30))
        assertEquals(listOf(5_000L, 6_000L, 5_700L, 5_700L, 5_700L), trend.map { it.balance })
        assertEquals(listOf("1", "2", "3", "4", "5"), trend.map { it.label })
    }

    @Test
    fun `tren saldo tidak melewati hari ini`() {
        val range = StatsRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "x")
        val trend = StatsCalculator.balanceTrend(0, emptyList(), range, today = LocalDate.of(2026, 11, 10))
        assertEquals(10, trend.size)
    }

    @Test
    fun `rentang panjang diringkas per akhir bulan`() {
        val range = StatsRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30), "x")
        val txs = listOf(tx(TransactionType.INCOME, 100, 5))
        val trend = StatsCalculator.balanceTrend(0, txs, range, today = LocalDate.of(2026, 11, 30))
        assertEquals(11, trend.size)
        assertEquals(100L, trend.last().balance)
        assertEquals(0L, trend.first().balance)
    }
}

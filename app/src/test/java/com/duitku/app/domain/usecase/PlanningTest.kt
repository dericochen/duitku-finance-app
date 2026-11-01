package com.duitku.app.domain.usecase

import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.model.DebtState
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.GoalContribution
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.domain.usecase.planning.DebtCalculator
import com.duitku.app.domain.usecase.planning.DebtReminderPolicy
import com.duitku.app.domain.usecase.planning.GoalCalculator
import com.duitku.app.domain.usecase.planning.RecurrenceCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {

    private fun dt(y: Int, m: Int, d: Int, h: Int = 8) = LocalDateTime.of(y, m, d, h, 0)

    // ---------- Transaksi berulang ----------

    @Test
    fun `bulanan tanggal 31 tidak melorot setelah bulan pendek`() {
        val start = dt(2026, 1, 31)
        val dates = (0L..3L).map { RecurrenceCalculator.occurrence(start, RecurrenceFrequency.MONTHLY, 1, it).toLocalDate() }
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            dates,
        )
    }

    @Test
    fun `interval dua minggu dan tahunan`() {
        assertEquals(dt(2026, 1, 29), RecurrenceCalculator.occurrence(dt(2026, 1, 1), RecurrenceFrequency.WEEKLY, 4, 1))
        assertEquals(dt(2028, 2, 29), RecurrenceCalculator.occurrence(dt(2024, 2, 29), RecurrenceFrequency.YEARLY, 1, 4))
        assertEquals(dt(2027, 2, 28), RecurrenceCalculator.occurrence(dt(2024, 2, 29), RecurrenceFrequency.YEARLY, 1, 3))
    }

    @Test
    fun `kemunculan jatuh tempo mencakup yang terlewat sampai sekarang`() {
        val start = dt(2026, 11, 1)
        val due = RecurrenceCalculator.dueOccurrences(start, RecurrenceFrequency.DAILY, 1, dt(2026, 11, 1), dt(2026, 11, 4, 9), null)
        assertEquals(4, due.size)
        assertEquals(listOf(0L, 1L, 2L, 3L), due.map { it.index })
        // Pukul 07.00 belum waktunya untuk kemunculan hari itu.
        val early = RecurrenceCalculator.dueOccurrences(start, RecurrenceFrequency.DAILY, 1, dt(2026, 11, 1), dt(2026, 11, 4, 7), null)
        assertEquals(3, early.size)
    }

    @Test
    fun `tidak melewati tanggal berakhir dan dimulai dari jadwal berikutnya`() {
        val start = dt(2026, 1, 15)
        val due = RecurrenceCalculator.dueOccurrences(
            start, RecurrenceFrequency.MONTHLY, 1, nextDue = dt(2026, 3, 15), now = dt(2026, 12, 1), end = dt(2026, 5, 31),
        )
        assertEquals(listOf(dt(2026, 3, 15), dt(2026, 4, 15), dt(2026, 5, 15)), due.map { it.at })
    }

    @Test
    fun `catch up dibatasi agar perangkat lama mati tidak membanjiri data`() {
        val due = RecurrenceCalculator.dueOccurrences(dt(2020, 1, 1), RecurrenceFrequency.DAILY, 1, dt(2020, 1, 1), dt(2026, 11, 1), null)
        assertEquals(RecurrenceCalculator.MAX_CATCH_UP, due.size)
    }

    @Test
    fun `kemunculan pertama pada atau setelah waktu tertentu`() {
        val start = dt(2026, 1, 25)
        val first = RecurrenceCalculator.firstAtOrAfter(start, RecurrenceFrequency.MONTHLY, 1, dt(2026, 11, 1))
        assertEquals(dt(2026, 11, 25), first.at)
        assertEquals(dt(2026, 11, 25), RecurrenceCalculator.firstAtOrAfter(start, RecurrenceFrequency.MONTHLY, 1, dt(2026, 11, 25)).at)
        assertEquals(start, RecurrenceCalculator.firstAtOrAfter(start, RecurrenceFrequency.MONTHLY, 1, dt(2025, 1, 1)).at)
    }

    // ---------- Target tabungan ----------

    private val today = LocalDate.of(2026, 11, 1)
    private fun millis(date: LocalDate) = date.atTime(10, 0).toEpochMillis()

    private fun goal(saved: Long, target: Long = 10_000_000, deadline: LocalDate? = null) = SavingGoal(
        id = 1, name = "Liburan", targetAmount = target, savedAmount = saved,
        deadline = deadline?.let { millis(it) }, createdAt = millis(today.minusDays(200)),
    )

    @Test
    fun `perkiraan tercapai dari laju menabung 90 hari terakhir`() {
        // 3.000.000 dalam 90 hari = ~33.333/hari; sisa 7.000.000 -> ~210 hari.
        val contributions = listOf(GoalContribution(1, 1, 3_000_000, millis(today.minusDays(30))))
        val est = GoalCalculator.estimate(goal(saved = 3_000_000), contributions, today)
        assertNotNull(est.estimatedDate)
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, est.estimatedDate)
        assertTrue("diperkirakan sekitar 210 hari, dapat $days", days in 205..215)
    }

    @Test
    fun `tanpa setoran perkiraan tidak tersedia dan target tercapai langsung selesai`() {
        assertNull(GoalCalculator.estimate(goal(saved = 0), emptyList(), today).estimatedDate)
        assertEquals(today, GoalCalculator.estimate(goal(saved = 10_000_000), emptyList(), today).estimatedDate)
    }

    @Test
    fun `setoran per bulan yang dibutuhkan dan status jalur`() {
        val deadline = today.plusDays(300) // sekitar 10 bulan
        val onTrack = GoalCalculator.estimate(
            goal(saved = 5_000_000, deadline = deadline),
            listOf(GoalContribution(1, 1, 5_000_000, millis(today.minusDays(10)))), today,
        )
        assertEquals(500_000L, onTrack.neededPerMonth)
        assertEquals(true, onTrack.onTrack)

        val behind = GoalCalculator.estimate(
            goal(saved = 100_000, deadline = today.plusDays(60)),
            listOf(GoalContribution(1, 1, 100_000, millis(today.minusDays(10)))), today,
        )
        assertEquals(false, behind.onTrack)
    }

    @Test
    fun `persentase dan sisa target`() {
        assertEquals(25, goal(2_500_000).percent)
        assertEquals(100, goal(12_000_000).percent)
        assertEquals(0, goal(12_000_000).remaining)
        assertTrue(goal(10_000_000).isCompleted)
    }

    // ---------- Utang piutang ----------

    private fun debt(due: LocalDate?, principal: Long = 1_000_000, paidOff: Boolean = false) = Debt(
        id = 1, kind = DebtKind.DEBT, partyName = "Budi", principalAmount = principal,
        dueDate = due?.let { millis(it) }, isPaidOff = paidOff,
    )

    @Test
    fun `status utang mengikuti jatuh tempo dan pelunasan`() {
        assertEquals(DebtState.OVERDUE, DebtCalculator.state(DebtWithPaid(debt(today.minusDays(1)), 0), today))
        assertEquals(DebtState.DUE_SOON, DebtCalculator.state(DebtWithPaid(debt(today.plusDays(3)), 0), today))
        assertEquals(DebtState.DUE_SOON, DebtCalculator.state(DebtWithPaid(debt(today), 0), today))
        assertEquals(DebtState.ACTIVE, DebtCalculator.state(DebtWithPaid(debt(today.plusDays(30)), 0), today))
        assertEquals(DebtState.ACTIVE, DebtCalculator.state(DebtWithPaid(debt(null), 0), today))
        assertEquals(DebtState.PAID, DebtCalculator.state(DebtWithPaid(debt(today.minusDays(9)), 1_000_000), today))
        assertEquals(DebtState.PAID, DebtCalculator.state(DebtWithPaid(debt(today.minusDays(9), paidOff = true), 0), today))
    }

    @Test
    fun `sisa dan persen pembayaran`() {
        val item = DebtWithPaid(debt(null, principal = 1_000_000), 250_000)
        assertEquals(750_000, item.remaining)
        assertEquals(25, item.percent)
    }

    @Test
    fun `kebijakan pengingat jatuh tempo`() {
        assertEquals(3, DebtReminderPolicy.levelFor(3))
        assertEquals(1, DebtReminderPolicy.levelFor(1))
        assertEquals(0, DebtReminderPolicy.levelFor(0))
        assertEquals(-1, DebtReminderPolicy.levelFor(-4))
        assertNull(DebtReminderPolicy.levelFor(2))
        assertNull(DebtReminderPolicy.levelFor(10))

        assertTrue(DebtReminderPolicy.shouldNotify(3, null, null, 100))
        assertFalse(DebtReminderPolicy.shouldNotify(3, 3, 100, 100))      // sudah diberitahu di level itu
        assertTrue(DebtReminderPolicy.shouldNotify(1, 3, 100, 102))       // naik level
        assertFalse(DebtReminderPolicy.shouldNotify(-1, -1, 100, 102))    // terlambat: belum 3 hari
        assertTrue(DebtReminderPolicy.shouldNotify(-1, -1, 100, 103))     // terlambat: ulangi tiap 3 hari
        assertFalse(DebtReminderPolicy.shouldNotify(null, null, null, 100))
    }
}

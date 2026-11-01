package com.duitku.app.core.util

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodCalculatorTest {

    @Test
    fun `startDay 1 sama dengan bulan kalender`() {
        val range = PeriodCalculator.rangeOf(YearMonth.of(2026, 2), 1)
        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
        assertEquals(28, range.dayCount)
    }

    @Test
    fun `startDay 25 periode November berjalan 25 Oktober sampai 24 November`() {
        val range = PeriodCalculator.rangeOf(YearMonth.of(2026, 11), 25)
        assertEquals(LocalDate.of(2026, 10, 25), range.start)
        assertEquals(LocalDate.of(2026, 11, 24), range.endInclusive)
    }

    @Test
    fun `periode lintas tahun dihitung benar`() {
        val range = PeriodCalculator.rangeOf(YearMonth.of(2027, 1), 25)
        assertEquals(LocalDate.of(2026, 12, 25), range.start)
        assertEquals(LocalDate.of(2027, 1, 24), range.endInclusive)
    }

    @Test
    fun `periodOf menentukan periode dari tanggal`() {
        assertEquals(YearMonth.of(2026, 11), PeriodCalculator.periodOf(LocalDate.of(2026, 10, 25), 25))
        assertEquals(YearMonth.of(2026, 11), PeriodCalculator.periodOf(LocalDate.of(2026, 11, 24), 25))
        assertEquals(YearMonth.of(2026, 12), PeriodCalculator.periodOf(LocalDate.of(2026, 11, 25), 25))
        assertEquals(YearMonth.of(2027, 1), PeriodCalculator.periodOf(LocalDate.of(2026, 12, 31), 25))
        assertEquals(YearMonth.of(2026, 11), PeriodCalculator.periodOf(LocalDate.of(2026, 11, 30), 1))
    }

    @Test
    fun `startDay di atas 28 dibatasi ke 28`() {
        assertEquals(28, PeriodCalculator.clampStartDay(31))
        assertEquals(1, PeriodCalculator.clampStartDay(0))
    }

    @Test
    fun `setiap tanggal masuk tepat satu periode tanpa celah`() {
        val startDay = 25
        var date = LocalDate.of(2026, 1, 1)
        while (date.isBefore(LocalDate.of(2027, 1, 1))) {
            val ym = PeriodCalculator.periodOf(date, startDay)
            assertTrue("$date harus berada di periode $ym", date in PeriodCalculator.rangeOf(ym, startDay))
            assertFalse(date in PeriodCalculator.rangeOf(ym.plusMonths(1), startDay))
            assertFalse(date in PeriodCalculator.rangeOf(ym.minusMonths(1), startDay))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `key dan konversi YearMonth`() {
        assertEquals(202611, YearMonth.of(2026, 11).toKey())
        assertEquals(YearMonth.of(2026, 11), 202611.toYearMonth())
    }

    @Test
    fun `monthsBetween inklusif dan berurutan`() {
        val months = PeriodCalculator.monthsBetween(YearMonth.of(2026, 11), YearMonth.of(2027, 2))
        assertEquals(4, months.size)
        assertEquals(YearMonth.of(2027, 2), months.last())
        assertTrue(PeriodCalculator.monthsBetween(YearMonth.of(2027, 2), YearMonth.of(2026, 11)).isEmpty())
    }
}

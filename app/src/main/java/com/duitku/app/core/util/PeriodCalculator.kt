package com.duitku.app.core.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Rentang satu periode bulanan. [start] dan [endInclusive] adalah tanggal kalender lokal.
 */
data class PeriodRange(
    val month: YearMonth,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    val key: Int get() = month.toKey()
    val dayCount: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long = start.startOfDayMillis(zone)
    fun endMillis(zone: ZoneId = ZoneId.systemDefault()): Long = endInclusive.endOfDayMillis(zone)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

/**
 * Menghitung periode bulanan dengan "tanggal awal bulan" yang bisa diatur (misal tanggal gajian = 25).
 *
 * Aturan pelabelan: periode diberi nama dari bulan tempat periode itu BERAKHIR.
 * Contoh startDay = 25: periode "November 2026" = 25 Okt 2026 s.d. 24 Nov 2026.
 * Jika startDay = 1, periode sama dengan bulan kalender biasa.
 */
object PeriodCalculator {
    private const val MIN_START_DAY = 1
    private const val MAX_START_DAY = 28

    fun clampStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

    fun rangeOf(month: YearMonth, startDay: Int): PeriodRange {
        val d = clampStartDay(startDay)
        return if (d == 1) {
            PeriodRange(month, month.atDay(1), month.atEndOfMonth())
        } else {
            PeriodRange(
                month = month,
                start = month.minusMonths(1).atDay(d),
                endInclusive = month.atDay(d).minusDays(1),
            )
        }
    }

    /** Periode yang memuat [date]. */
    fun periodOf(date: LocalDate, startDay: Int): YearMonth {
        val d = clampStartDay(startDay)
        val ym = YearMonth.from(date)
        return if (d == 1 || date.dayOfMonth < d) ym else ym.plusMonths(1)
    }

    fun periodOfMillis(millis: Long, startDay: Int, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        periodOf(millis.toLocalDate(zone), startDay)

    /** Daftar periode berurutan dari [from] sampai [to] (inklusif). */
    fun monthsBetween(from: YearMonth, to: YearMonth): List<YearMonth> {
        if (from.isAfter(to)) return emptyList()
        val result = ArrayList<YearMonth>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            result += cursor
            cursor = cursor.plusMonths(1)
        }
        return result
    }
}

fun YearMonth.toKey(): Int = year * 100 + monthValue

fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)

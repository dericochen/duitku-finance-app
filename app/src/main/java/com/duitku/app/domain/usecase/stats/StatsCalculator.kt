package com.duitku.app.domain.usecase.stats

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.BalancePoint
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryStat
import com.duitku.app.domain.model.StatsMode
import com.duitku.app.domain.model.StatsRange
import com.duitku.app.domain.model.StatsRequest
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Perhitungan statistik murni (tanpa database) agar mudah diuji. */
object StatsCalculator {

    /** Menentukan rentang tanggal sesuai mode. Minggu dimulai hari Senin. */
    fun resolveRange(request: StatsRequest, startDay: Int, today: LocalDate = LocalDate.now()): StatsRange =
        when (request.mode) {
            StatsMode.WEEK -> {
                val start = request.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = start.plusDays(6)
                StatsRange(start, end, "${DateFormats.dayMonth(start)} - ${DateFormats.date(end)}")
            }
            StatsMode.MONTH -> {
                val period = PeriodCalculator.rangeOf(PeriodCalculator.periodOf(request.anchor, startDay), startDay)
                StatsRange(period.start, period.endInclusive, DateFormats.monthYear(period.month))
            }
            StatsMode.YEAR -> {
                val year = request.anchor.year
                StatsRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "Tahun $year")
            }
            StatsMode.CUSTOM -> {
                val from = request.customFrom ?: request.anchor.minusDays(29)
                val to = request.customTo ?: request.anchor
                val (s, e) = if (from.isAfter(to)) to to from else from to to
                StatsRange(s, e, "${DateFormats.date(s)} - ${DateFormats.date(e)}")
            }
            StatsMode.ROLLING_12 -> {
                val current = PeriodCalculator.periodOf(today, startDay)
                val first = PeriodCalculator.rangeOf(current.minusMonths(11), startDay)
                val last = PeriodCalculator.rangeOf(current, startDay)
                StatsRange(first.start, last.endInclusive, "12 bulan terakhir")
            }
        }

    /** Rentang pembanding dengan panjang yang sama tepat sebelum [range] (bulan lalu, minggu lalu, dst.). */
    fun previousRange(request: StatsRequest, range: StatsRange, startDay: Int): StatsRange = when (request.mode) {
        StatsMode.WEEK -> resolveRange(request.copy(anchor = range.start.minusWeeks(1)), startDay)
        StatsMode.MONTH -> {
            val prev = PeriodCalculator.periodOf(range.start, startDay).minusMonths(1)
            val r = PeriodCalculator.rangeOf(prev, startDay)
            StatsRange(r.start, r.endInclusive, DateFormats.monthYear(prev))
        }
        StatsMode.YEAR -> resolveRange(request.copy(anchor = range.start.minusYears(1)), startDay)
        else -> {
            val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
            val end = range.start.minusDays(1)
            val start = end.minusDays(days - 1)
            StatsRange(start, end, "${DateFormats.date(start)} - ${DateFormats.date(end)}")
        }
    }

    fun comparisonLabel(mode: StatsMode): String = when (mode) {
        StatsMode.WEEK -> "minggu lalu"
        StatsMode.MONTH -> "bulan lalu"
        StatsMode.YEAR -> "tahun lalu"
        StatsMode.CUSTOM, StatsMode.ROLLING_12 -> "periode sebelumnya"
    }

    /**
     * Statistik per kategori utama (subkategori sudah masuk induknya karena categoryId = induk).
     * @param totals categoryId -> (total, jumlah transaksi)
     */
    fun categoryStats(
        totals: Map<Long?, Pair<Long, Int>>,
        categories: List<Category>,
        previousTotals: Map<Long?, Long> = emptyMap(),
        fallbackColor: Int = 0xFF546E7A.toInt(),
    ): List<CategoryStat> {
        val sum = totals.values.sumOf { it.first }
        if (sum <= 0) return emptyList()
        val byId = categories.associateBy { it.id }
        return totals.entries
            .filter { it.value.first > 0 }
            .map { (id, v) ->
                val cat = id?.let(byId::get)
                CategoryStat(
                    categoryId = id,
                    name = cat?.name ?: "Tanpa kategori",
                    colorArgb = cat?.colorArgb ?: fallbackColor,
                    iconKey = cat?.iconKey ?: "other",
                    total = v.first,
                    count = v.second,
                    percent = Math.round(v.first * 1000.0 / sum) / 10.0,
                    previousTotal = previousTotals[id] ?: 0L,
                )
            }
            .sortedByDescending { it.total }
    }

    /**
     * Tren saldo total. Rentang pendek (<= [DAILY_LIMIT] hari) per hari, selebihnya per akhir bulan kalender.
     * @param opening saldo total tepat sebelum [range] dimulai.
     */
    fun balanceTrend(opening: Long, txsAscending: List<Transaction>, range: StatsRange, today: LocalDate = LocalDate.now()): List<BalancePoint> {
        val lastDay = minOf(range.endInclusive, today).let { if (it.isBefore(range.start)) range.endInclusive else it }
        val deltaByDay = txsAscending.groupBy { it.occurredAt.toLocalDate() }.mapValues { (_, list) ->
            list.sumOf { t ->
                when (t.type) {
                    TransactionType.INCOME -> t.amount
                    TransactionType.EXPENSE -> -t.amount
                    TransactionType.TRANSFER -> 0L
                }
            }
        }
        val days = ChronoUnit.DAYS.between(range.start, lastDay).toInt() + 1
        if (days <= 0) return emptyList()

        var balance = opening
        val daily = ArrayList<Pair<LocalDate, Long>>(days)
        var d = range.start
        while (!d.isAfter(lastDay)) {
            balance += deltaByDay[d] ?: 0L
            daily += d to balance
            d = d.plusDays(1)
        }
        if (days <= DAILY_LIMIT) {
            return daily.map { (date, value) -> BalancePoint(date.dayOfMonth.toString(), value) }
        }
        // Ambil saldo hari terakhir tiap bulan kalender.
        return daily.groupBy { YearMonth.from(it.first) }.map { (ym, list) ->
            BalancePoint(DateFormats.monthShort(ym), list.last().second)
        }
    }

    const val DAILY_LIMIT = 93
}

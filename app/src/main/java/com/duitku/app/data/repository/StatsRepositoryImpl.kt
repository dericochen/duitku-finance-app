package com.duitku.app.data.repository

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.startOfDayMillis
import com.duitku.app.data.local.dao.CategoryDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.mapper.toDomain
import com.duitku.app.domain.model.Comparison
import com.duitku.app.domain.model.MonthBar
import com.duitku.app.domain.model.StatsData
import com.duitku.app.domain.model.StatsMode
import com.duitku.app.domain.model.StatsRequest
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatsRepository
import com.duitku.app.domain.usecase.stats.StatsCalculator
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
) : StatsRepository {

    override fun observe(request: StatsRequest): Flow<StatsData> =
        transactionDao.observeVersion().map { load(request) }

    override suspend fun load(request: StatsRequest): StatsData {
        val startDay = settingsRepository.current().monthStartDay
        val range = StatsCalculator.resolveRange(request, startDay)
        val prevRange = StatsCalculator.previousRange(request, range, startDay)
        val start = range.start.startOfDayMillis()
        val end = range.endInclusive.endOfDayMillis()

        val categories = categoryDao.getAll().map { it.toDomain() }
        val types = transactionDao.getTypeTotals(start, end).associateBy { it.type }
        val income = types[TransactionType.INCOME]?.total ?: 0L
        val expense = types[TransactionType.EXPENSE]?.total ?: 0L
        val count = types.values.sumOf { it.txCount }

        val prevStart = prevRange.start.startOfDayMillis()
        val prevEnd = prevRange.endInclusive.endOfDayMillis()
        val prevTypes = transactionDao.getTypeTotals(prevStart, prevEnd).associateBy { it.type }

        val expenseRows = transactionDao.getCategoryTotals(TransactionType.EXPENSE, start, end)
        val incomeRows = transactionDao.getCategoryTotals(TransactionType.INCOME, start, end)
        val prevExpenseRows = transactionDao.getCategoryTotals(TransactionType.EXPENSE, prevStart, prevEnd)

        val expenseStats = StatsCalculator.categoryStats(
            totals = expenseRows.associate { it.categoryId to (it.total to it.txCount) },
            categories = categories,
            previousTotals = prevExpenseRows.associate { it.categoryId to it.total },
        )
        val incomeStats = StatsCalculator.categoryStats(
            totals = incomeRows.associate { it.categoryId to (it.total to it.txCount) },
            categories = categories,
        )

        val opening = transactionDao.getBalancesBefore(start).sumOf { it.balance }
        val txs = transactionDao.getBetweenAscending(start, end).map { it.toDomain() }
        val trend = StatsCalculator.balanceTrend(opening, txs, range)

        val monthsBack = if (request.mode == StatsMode.ROLLING_12) 12 else request.monthsBack
        return StatsData(
            range = range,
            income = income,
            expense = expense,
            transactionCount = count,
            expenseByCategory = expenseStats,
            incomeByCategory = incomeStats,
            monthlyBars = monthlyBars(range.endInclusive, monthsBack, startDay),
            balanceTrend = trend,
            expenseComparison = Comparison(expense, prevTypes[TransactionType.EXPENSE]?.total ?: 0L),
            incomeComparison = Comparison(income, prevTypes[TransactionType.INCOME]?.total ?: 0L),
            comparisonLabel = StatsCalculator.comparisonLabel(request.mode),
        )
    }

    /** Pemasukan vs pengeluaran per periode bulanan, berakhir di periode yang memuat [endDate]. */
    private suspend fun monthlyBars(endDate: LocalDate, count: Int, startDay: Int): List<MonthBar> {
        val last = PeriodCalculator.periodOf(minOf(endDate, LocalDate.now()), startDay)
        return PeriodCalculator.monthsBetween(last.minusMonths((count - 1).toLong()), last).map { ym ->
            val r = PeriodCalculator.rangeOf(ym, startDay)
            val totals = transactionDao.getTypeTotals(r.startMillis(), r.endMillis()).associateBy { it.type }
            MonthBar(
                label = DateFormats.monthShort(ym),
                income = totals[TransactionType.INCOME]?.total ?: 0L,
                expense = totals[TransactionType.EXPENSE]?.total ?: 0L,
            )
        }
    }
}

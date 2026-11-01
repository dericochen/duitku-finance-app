package com.duitku.app.data.repository

import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.data.local.dao.BudgetDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.entity.BudgetEntity
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.budget.BudgetEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
) : BudgetRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLines(periodYm: Int): Flow<List<BudgetLine>> =
        settingsRepository.settings.map { it.monthStartDay }.distinctUntilChanged().flatMapLatest { day ->
            val range = PeriodCalculator.rangeOf(periodYm.toYearMonth(), day)
            // Hitung ulang setiap kali anggaran atau transaksi periode ini berubah.
            combine(
                budgetDao.observeForPeriod(periodYm),
                transactionDao.observeTypeTotals(range.startMillis(), range.endMillis()),
            ) { _, _ -> Unit }.map { getLines(periodYm) }
        }
    override suspend fun getLines(periodYm: Int): List<BudgetLine> {
        val startDay = settingsRepository.current().monthStartDay
        val periods = (0..BudgetEngine.MAX_CHAIN).map { periodYm.toYearMonth().minusMonths(it.toLong()).toKey() }
        val budgets = periods.associateWith { p -> budgetDao.getForPeriod(p).map { it.toDomain() } }
        val spent = periods.filter { budgets[it].orEmpty().isNotEmpty() || it == periodYm }.associateWith { p ->
            val range = PeriodCalculator.rangeOf(p.toYearMonth(), startDay)
            val rows = transactionDao.getCategoryTotals(TransactionType.EXPENSE, range.startMillis(), range.endMillis())
            val map = HashMap<Long?, Long>()
            rows.forEach { if (it.categoryId != null) map[it.categoryId] = it.total }
            map[null] = rows.sumOf { it.total }
            map
        }
        return BudgetEngine.lines(periodYm, budgets, spent)
    }

    override suspend fun get(periodYm: Int, categoryId: Long?): Budget? =
        budgetDao.find(periodYm, categoryId)?.toDomain()

    override suspend fun save(budget: Budget): Long {
        val existing = budgetDao.find(budget.periodYm, budget.categoryId)
        return if (existing != null) {
            budgetDao.update(existing.copy(amount = budget.amount, rollover = budget.rollover))
            existing.id
        } else {
            budgetDao.insert(budget.copy(id = 0).toEntity())
        }
    }

    override suspend fun delete(id: Long) = budgetDao.deleteById(id)

    override suspend fun copyFromPrevious(periodYm: Int): Int {
        val prev = periodYm.toYearMonth().minusMonths(1).toKey()
        var copied = 0
        for (b in budgetDao.getForPeriod(prev)) {
            if (budgetDao.find(periodYm, b.categoryId) == null) {
                budgetDao.insert(b.copy(id = 0, periodYm = periodYm))
                copied++
            }
        }
        return copied
    }

    private fun BudgetEntity.toDomain() = Budget(id, periodYm, categoryId, amount, rollover)
    private fun Budget.toEntity() = BudgetEntity(id, periodYm, categoryId, amount, rollover)
}

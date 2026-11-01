package com.duitku.app.domain.usecase.budget

import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine

/**
 * Menghitung anggaran + rollover. Murni (tanpa database) agar mudah diuji.
 *
 * Rollover: sisa anggaran periode sebelumnya (bila budget sebelumnya memilih rollover)
 * ditambahkan ke batas periode ini untuk kategori yang sama. Rantai dibatasi [MAX_CHAIN] periode.
 */
object BudgetEngine {
    const val MAX_CHAIN = 12

    /**
     * @param budgetsByPeriod anggaran per periode (key YYYYMM).
     * @param spentByPeriod pengeluaran per periode: categoryId (null = total semua kategori) -> jumlah.
     */
    fun lines(
        periodYm: Int,
        budgetsByPeriod: Map<Int, List<Budget>>,
        spentByPeriod: Map<Int, Map<Long?, Long>>,
    ): List<BudgetLine> {
        val current = budgetsByPeriod[periodYm].orEmpty()
        return current.map { b ->
            BudgetLine(
                budget = b,
                rollIn = rolloverIn(periodYm, b.categoryId, budgetsByPeriod, spentByPeriod, 0),
                spent = spentByPeriod[periodYm]?.get(b.categoryId) ?: 0L,
            )
        }
    }

    private fun rolloverIn(
        periodYm: Int,
        categoryId: Long?,
        budgetsByPeriod: Map<Int, List<Budget>>,
        spentByPeriod: Map<Int, Map<Long?, Long>>,
        depth: Int,
    ): Long {
        if (depth >= MAX_CHAIN) return 0L
        val prev = periodYm.toYearMonth().minusMonths(1).toKey()
        val prevBudget = budgetsByPeriod[prev]?.firstOrNull { it.categoryId == categoryId } ?: return 0L
        if (!prevBudget.rollover) return 0L
        val carried = rolloverIn(prev, categoryId, budgetsByPeriod, spentByPeriod, depth + 1)
        val spent = spentByPeriod[prev]?.get(categoryId) ?: 0L
        return (prevBudget.amount + carried - spent).coerceAtLeast(0L)
    }
}

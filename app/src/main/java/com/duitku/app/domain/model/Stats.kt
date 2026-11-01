package com.duitku.app.domain.model

import java.time.LocalDate

enum class StatsMode(val label: String) {
    WEEK("Mingguan"),
    MONTH("Bulanan"),
    YEAR("Tahunan"),
    CUSTOM("Custom"),
    ROLLING_12("12 Bulan Terakhir"),
}

/** Permintaan statistik. [anchor] menentukan minggu/bulan/tahun yang ditampilkan. */
data class StatsRequest(
    val mode: StatsMode = StatsMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    /** Jumlah bulan pada diagram batang (6 atau 12). */
    val monthsBack: Int = 6,
)

data class StatsRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val label: String,
)

data class CategoryStat(
    val categoryId: Long?,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val total: Long,
    val count: Int,
    /** Persentase dari total (0..100, satu desimal). */
    val percent: Double,
    val previousTotal: Long = 0,
)

data class MonthBar(
    val label: String,
    val income: Long,
    val expense: Long,
)

data class BalancePoint(
    val label: String,
    val balance: Long,
)

/** Perbandingan satu angka dengan periode pembanding. [percent] null bila periode pembanding bernilai 0. */
data class Comparison(
    val current: Long,
    val previous: Long,
) {
    val delta: Long get() = current - previous
    val percent: Double? get() = if (previous == 0L) null else (delta * 100.0) / previous
}

data class StatsData(
    val range: StatsRange,
    val income: Long,
    val expense: Long,
    val transactionCount: Int,
    val expenseByCategory: List<CategoryStat>,
    val incomeByCategory: List<CategoryStat>,
    val monthlyBars: List<MonthBar>,
    val balanceTrend: List<BalancePoint>,
    val expenseComparison: Comparison,
    val incomeComparison: Comparison,
    val comparisonLabel: String,
) {
    val net: Long get() = income - expense

    /** Persentase pemasukan yang tidak dibelanjakan; null bila tidak ada pemasukan. */
    val savingsRate: Double? get() = if (income <= 0) null else (net * 100.0) / income
    val topExpense: List<CategoryStat> get() = expenseByCategory.take(5)
    val isEmpty: Boolean get() = transactionCount == 0
}

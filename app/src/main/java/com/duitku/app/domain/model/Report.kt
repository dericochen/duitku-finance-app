package com.duitku.app.domain.model

import java.time.LocalDate

/** Opsi ekspor yang dipilih pengguna. */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val kind: ExportKind = ExportKind.MONTHLY,
    /** Periode awal & akhir (YYYYMM). Untuk MONTHLY keduanya sama. */
    val fromPeriodYm: Int,
    val toPeriodYm: Int,
    /** null = semua dompet. */
    val walletId: Long? = null,
    val includeCharts: Boolean = true,
    val theme: PdfTheme = PdfTheme.CLASSIC,
    /** Saldo sesuai rekening bank yang diisi pengguna: walletId -> saldo. */
    val bankBalances: Map<Long, Long> = emptyMap(),
    /** Catatan bulan; bila null memakai catatan yang tersimpan pada arsip. */
    val userNote: String? = null,
)

data class ExportProgress(val percent: Int, val label: String)

data class ExportResult(
    val historyId: Long,
    val fileName: String,
    val format: ExportFormat,
    val sizeBytes: Long,
    val documentNumber: String?,
    val isFinal: Boolean,
)

// ---------- Laporan bulanan ----------

data class ReportMeta(
    val title: String,
    val ownerName: String,
    val periodLabel: String,
    val rangeLabel: String,
    val documentNumber: String,
    val printedAt: Long,
    val isFinal: Boolean,
    val revisionLabel: String,
    val currency: String,
    val walletScope: String,
    val checksum: String,
    val startMillis: Long,
    val endMillis: Long,
)

data class ReportTx(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val at: Long,
    val walletName: String,
    val toWalletName: String?,
    val categoryLabel: String,
    val note: String,
    val tags: List<String>,
    val recurring: Boolean,
)

data class ReportRow(
    val number: Int,
    val tx: ReportTx,
    /** Uang keluar (pengeluaran / transfer keluar pada tampilan satu dompet). */
    val debit: Long,
    /** Uang masuk (pemasukan / transfer masuk pada tampilan satu dompet). */
    val credit: Long,
    /** Transfer pada tampilan semua dompet: netral, tidak masuk debit/kredit maupun saldo berjalan. */
    val neutralTransfer: Boolean,
    val balanceAfter: Long,
)

data class ReportDay(
    val date: LocalDate,
    val rows: List<ReportRow>,
    val totalDebit: Long,
    val totalCredit: Long,
)

data class WalletSummaryRow(
    val name: String,
    val opening: Long,
    val income: Long,
    val expense: Long,
    val transferIn: Long,
    val transferOut: Long,
    val added: Long,
    val closing: Long,
)

data class CategoryAnalysisRow(
    val name: String,
    val count: Int,
    val total: Long,
    val percent: Double,
    val average: Long,
    val previousTotal: Long,
    val colorArgb: Int,
) {
    /** Perubahan terhadap bulan lalu dalam persen; null bila bulan lalu 0. */
    val changePercent: Double? get() = if (previousTotal == 0L) null else (total - previousTotal) * 100.0 / previousTotal
}

data class BudgetReportLine(
    val name: String,
    val amount: Long,
    val rollIn: Long,
    val spent: Long,
    val remaining: Long,
    val percent: Int,
    val status: BudgetStatus,
    val rollover: Boolean,
    val isTotal: Boolean,
)

data class BudgetReport(
    val lines: List<BudgetReportLine>,
    /** Sisa yang dibawa ke bulan depan (hanya anggaran dengan opsi rollover). */
    val rolloverNext: List<Pair<String, Long>>,
)

data class TrendData(
    /** Selisih (masuk - keluar) per hari, urut tanggal. */
    val dailyCashflow: List<Pair<LocalDate, Long>>,
    val dailyBalance: List<Pair<LocalDate, Long>>,
    /** Pemasukan & pengeluaran per minggu ke-1..n dalam periode. */
    val weekly: List<Triple<String, Long, Long>>,
    /** Total pengeluaran Senin..Minggu. */
    val weekdayExpense: List<Pair<String, Long>>,
)

data class ExecutiveSummary(
    val opening: Long,
    val income: Long,
    val expense: Long,
    val transferIn: Long,
    val transferOut: Long,
    val added: Long,
    val closing: Long,
    val net: Long,
    val savingsRate: Double?,
    val dayCount: Int,
    val avgDailyExpense: Long,
    val mostExpensiveDay: Pair<LocalDate, Long>?,
    val biggestExpense: ReportTx?,
    val biggestIncome: ReportTx?,
    val expenseVsPrevious: Comparison?,
    val incomeVsPrevious: Comparison?,
    val conclusion: String,
)

data class WalletReconciliation(
    val name: String,
    val expected: Long,
    val recorded: Long,
    val bankBalance: Long?,
) {
    val bankDifference: Long? get() = bankBalance?.let { recorded - it }
}

data class ReconciliationReport(
    val opening: Long,
    val income: Long,
    val expense: Long,
    val added: Long,
    val expectedClosing: Long,
    val recordedClosing: Long,
    val wallets: List<WalletReconciliation>,
) {
    val difference: Long get() = recordedClosing - expectedClosing
    val isBalanced: Boolean get() = difference == 0L
}

data class RecurringReport(
    val items: List<SnapRecurring>,
    val monthlyBurden: Long,
)

data class ClosingInfo(
    val userNote: String,
    val documentNumber: String,
    val checksum: String,
    val qrPayload: String,
)

data class ReportModel(
    val meta: ReportMeta,
    val summary: ExecutiveSummary,
    val wallets: List<WalletSummaryRow>,
    val days: List<ReportDay>,
    val expenseAnalysis: List<CategoryAnalysisRow>,
    val incomeAnalysis: List<CategoryAnalysisRow>,
    val budget: BudgetReport?,
    val trends: TrendData,
    val topExpense: List<ReportTx>,
    val topIncome: List<ReportTx>,
    val recurring: RecurringReport,
    val goals: List<SnapGoal>,
    val debts: List<SnapDebt>,
    val reconciliation: ReconciliationReport,
    val closing: ClosingInfo,
)

// ---------- Laporan rentang / tahunan ----------

data class AnnualMonthRow(
    val periodYm: Int,
    val label: String,
    val income: Long,
    val expense: Long,
    val net: Long,
    val closing: Long,
    val savingsRate: Double?,
    val isClosed: Boolean,
)

data class AnnualReportModel(
    val meta: ReportMeta,
    val months: List<AnnualMonthRow>,
    val totalIncome: Long,
    val totalExpense: Long,
    val totalNet: Long,
    val openingBalance: Long,
    val closingBalance: Long,
    val avgMonthlyIncome: Long,
    val avgMonthlyExpense: Long,
    val savingsRate: Double?,
    val expenseByCategory: List<CategoryAnalysisRow>,
    val incomeByCategory: List<CategoryAnalysisRow>,
    val mostExpensiveMonth: AnnualMonthRow?,
    val thriftiestMonth: AnnualMonthRow?,
    val transactionCount: Int,
    val allClosed: Boolean,
    val closingInfo: ClosingInfo,
)

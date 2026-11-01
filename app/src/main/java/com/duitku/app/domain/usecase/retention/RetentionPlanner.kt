package com.duitku.app.domain.usecase.retention

import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.domain.model.AppSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Hasil perhitungan masa retensi. */
data class RetentionWindow(
    /** Transaksi dengan waktu < [cutoffMillis] sudah melewati masa retensi. null = retensi "selamanya". */
    val cutoffMillis: Long?,
    /** Periode pertama (inklusif) yang masih dipertahankan. */
    val firstKeptPeriod: YearMonth?,
    val retentionMonths: Int,
) {
    val isForever: Boolean get() = cutoffMillis == null
}

/**
 * Aturan retensi: data dalam [AppSettings.retentionMonths] periode penuh TERAKHIR ditambah periode berjalan
 * tidak pernah dianggap kedaluwarsa. Dengan 12 bulan, pengguna selalu punya minimal 12 bulan penuh + bulan berjalan.
 */
object RetentionPlanner {
    const val WARNING_DAYS = 30L

    fun window(retentionMonths: Int, startDay: Int, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): RetentionWindow {
        if (retentionMonths == AppSettings.RETENTION_FOREVER) return RetentionWindow(null, null, retentionMonths)
        val current = PeriodCalculator.periodOf(today, startDay)
        val firstKept = current.minusMonths(retentionMonths.toLong())
        val cutoff = PeriodCalculator.rangeOf(firstKept, startDay).startMillis(zone)
        return RetentionWindow(cutoff, firstKept, retentionMonths)
    }

    /** Daftar periode (YYYYMM) yang seluruhnya berada sebelum [cutoffMillis] dan memuat data. */
    fun expiredPeriods(
        oldestTxMillis: Long?,
        window: RetentionWindow,
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<YearMonth> {
        val cutoff = window.cutoffMillis ?: return emptyList()
        val firstKept = window.firstKeptPeriod ?: return emptyList()
        val oldest = oldestTxMillis ?: return emptyList()
        if (oldest >= cutoff) return emptyList()
        val first = PeriodCalculator.periodOfMillis(oldest, startDay, zone)
        return PeriodCalculator.monthsBetween(first, firstKept.minusMonths(1))
    }

    /**
     * Apakah ada data yang akan kedaluwarsa dalam [WARNING_DAYS] hari ke depan (tetapi belum kedaluwarsa hari ini)?
     * Dipakai untuk notifikasi 30 hari sebelumnya.
     */
    fun expiresSoon(
        oldestTxMillis: Long?,
        retentionMonths: Int,
        startDay: Int,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val now = window(retentionMonths, startDay, today, zone)
        val later = window(retentionMonths, startDay, today.plusDays(WARNING_DAYS), zone)
        val oldest = oldestTxMillis ?: return false
        val nowCutoff = now.cutoffMillis ?: return false
        val laterCutoff = later.cutoffMillis ?: return false
        return oldest >= nowCutoff && oldest < laterCutoff
    }
}

package com.duitku.app.data.retention

import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.data.backup.BackupService
import com.duitku.app.data.local.dao.AuditLogDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.RetentionAction
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.ExportRepository
import com.duitku.app.domain.repository.PrepareResult
import com.duitku.app.domain.repository.PurgeResult
import com.duitku.app.domain.repository.RetentionRepository
import com.duitku.app.domain.repository.RetentionStatus
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.usecase.retention.RetentionPlanner
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retensi aman: data lama TIDAK pernah dihapus otomatis. Urutannya selalu
 * (1) tutup bulan -> snapshot permanen, (2) backup penuh, (3) PDF laporan tahunan, (4) tunggu konfirmasi pengguna,
 * (5) baru bersihkan detail transaksi mentah. Snapshot bulanan dan PDF yang pernah diekspor tidak ikut dihapus.
 */
@Singleton
class RetentionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val statementDao: StatementDao,
    private val auditLogDao: AuditLogDao,
    private val statements: StatementRepository,
    private val backups: BackupRepository,
    private val exports: ExportRepository,
    private val backupService: BackupService,
    private val settingsRepository: SettingsRepository,
) : RetentionRepository {

    override suspend fun status(): RetentionStatus = status(LocalDate.now())

    suspend fun status(today: LocalDate): RetentionStatus {
        val s = settingsRepository.current()
        val window = RetentionPlanner.window(s.retentionMonths, s.monthStartDay, today)
        val oldest = transactionDao.getOldestAt()
        val expiredPeriods = RetentionPlanner.expiredPeriods(oldest, window, s.monthStartDay)
        val expiredCount = window.cutoffMillis?.let { transactionDao.countBefore(it) } ?: 0
        return RetentionStatus(
            retentionMonths = s.retentionMonths, action = s.retentionAction, cutoffMillis = window.cutoffMillis,
            oldestTransactionAt = oldest, expiredTransactionCount = expiredCount,
            expiredPeriods = expiredPeriods.map { it.toKey() },
            expiresSoon = RetentionPlanner.expiresSoon(oldest, s.retentionMonths, s.monthStartDay, today),
            prepared = window.cutoffMillis != null && s.purgePreparedCutoff == window.cutoffMillis,
            preparedAt = s.purgePreparedAt,
        )
    }

    /** Periode kedaluwarsa yang masih punya transaksi tetapi belum punya arsip tertutup. */
    private suspend fun unarchived(periods: List<Int>): List<Int> {
        val closed = statementDao.getClosedPeriods().toSet()
        val startDay = settingsRepository.current().monthStartDay
        return periods.filter { ym ->
            val range = PeriodCalculator.rangeOf(ym.toYearMonth(), startDay)
            ym !in closed && transactionDao.getTypeTotals(range.startMillis(), range.endMillis()).sumOf { it.txCount } > 0
        }
    }

    override suspend fun prepare(onProgress: (Int, String) -> Unit): PrepareResult {
        val st = status()
        val cutoff = st.cutoffMillis
        if (cutoff == null || !st.hasExpiredData) return PrepareResult.NothingToDo
        try {
            // 1. Pastikan semua bulan kedaluwarsa punya snapshot tertutup.
            var closed = 0
            st.expiredPeriods.forEachIndexed { i, ym ->
                onProgress(5 + 30 * i / st.expiredPeriods.size, "Menutup ${DateFormats.monthYear(ym.toYearMonth())}")
                val active = statements.getActive(ym)
                if (active == null || !active.isClosed) {
                    when (val r = statements.close(ym)) {
                        is CloseResult.Closed -> closed++
                        is CloseResult.Failed -> return PrepareResult.Failed("Gagal menutup ${DateFormats.monthYear(ym.toYearMonth())}: ${r.message}")
                        CloseResult.AlreadyClosed, CloseResult.NoData -> Unit
                    }
                }
            }
            val missing = unarchived(st.expiredPeriods)
            if (missing.isNotEmpty()) return PrepareResult.Failed("Masih ada bulan yang belum terarsip: ${missing.joinToString()}")

            // 2. Backup penuh sebelum pembersihan.
            onProgress(45, "Membuat backup")
            val backup = backups.backupToConfiguredLocation(BackupKind.PRE_PURGE)

            // 3. PDF laporan tahunan per tahun yang terdampak.
            var reports = 0
            val byYear = st.expiredPeriods.groupBy { it / 100 }
            byYear.entries.forEachIndexed { i, (year, periods) ->
                onProgress(55 + 35 * i / byYear.size, "Membuat laporan tahunan $year")
                exports.export(ExportOptions(ExportFormat.PDF, ExportKind.ANNUAL, periods.min(), periods.max(), includeCharts = true))
                reports++
            }

            settingsRepository.update { it.copy(purgePreparedCutoff = cutoff, purgePreparedAt = System.currentTimeMillis()) }
            auditLogDao.insert(
                AuditLogEntity(
                    createdAt = System.currentTimeMillis(), entityType = "RETENTION", action = "PREPARE",
                    description = "Arsip disiapkan untuk ${st.expiredTransactionCount} transaksi (${st.expiredPeriods.size} bulan): " +
                        "$closed bulan ditutup, $reports laporan tahunan, backup ${backup.fileName}",
                ),
            )
            onProgress(100, "Selesai")
            return PrepareResult.Ready(closed, reports, backup.fileName, st.expiredTransactionCount)
        } catch (e: Exception) {
            return PrepareResult.Failed(e.message ?: "Gagal menyiapkan arsip")
        }
    }

    override suspend fun purge(): PurgeResult {
        val st = status()
        val cutoff = st.cutoffMillis ?: return PurgeResult.Refused("Masa retensi diatur Selamanya")
        if (st.action != RetentionAction.ASK_BEFORE_PURGE) return PurgeResult.Refused("Mode \"Pindahkan ke arsip saja\" aktif: data tidak dihapus")
        if (!st.hasExpiredData) return PurgeResult.Refused("Tidak ada data yang melewati masa retensi")
        if (!st.prepared) return PurgeResult.Refused("Siapkan arsip dan backup terlebih dahulu")
        if (unarchived(st.expiredPeriods).isNotEmpty()) return PurgeResult.Refused("Ada bulan yang belum terarsip; siapkan arsip ulang")

        val deleted = backupService.purgeBefore(cutoff)
        settingsRepository.update { it.copy(purgePreparedCutoff = 0L, purgePreparedAt = 0L) }
        auditLogDao.insert(
            AuditLogEntity(
                createdAt = System.currentTimeMillis(), entityType = "RETENTION", action = "PURGE",
                description = "$deleted transaksi mentah sebelum ${DateFormats.date(cutoff)} dibersihkan setelah konfirmasi pengguna; snapshot bulanan tetap tersimpan",
            ),
        )
        return PurgeResult.Done(deleted)
    }
}

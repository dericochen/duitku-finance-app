package com.duitku.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duitku.app.core.notification.NotificationChannels
import com.duitku.app.core.notification.NotificationHelper
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.startOfDayMillis
import com.duitku.app.domain.model.BackupFrequency
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.RetentionAction
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.PrepareResult
import com.duitku.app.domain.repository.RetentionRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** Backup terjadwal ke folder pilihan pengguna; menyisakan sejumlah backup terbaru. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onboardingDone || settings.backupFrequency == BackupFrequency.OFF) return Result.success()
        return try {
            val outcome = backups.backupToConfiguredLocation(BackupKind.AUTO)
            if (settings.backupFolderUri.isNotBlank() && !outcome.location.startsWith("content://")) {
                notifications.notify(
                    NotificationChannels.DATA, 3003, "Folder backup tidak dapat diakses",
                    "Backup disimpan di penyimpanan aplikasi. Pilih ulang folder di Backup & Restore.",
                )
            }
            Result.success()
        } catch (e: Exception) {
            notifications.notify(NotificationChannels.DATA, 3004, "Backup otomatis gagal", e.message ?: "Terjadi kesalahan saat membuat backup.")
            Result.success() // jadwal berikutnya akan mencoba lagi; tidak perlu retry beruntun
        }
    }

    companion object {
        const val UNIQUE_NAME = "auto-backup"
    }
}

/**
 * Pemeriksa retensi harian. Tidak pernah menghapus data: hanya memperingatkan 30 hari sebelumnya dan
 * menyiapkan arsip (tutup bulan + backup + PDF laporan tahunan). Pembersihan menunggu konfirmasi pengguna.
 */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val retention: RetentionRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("retention_notices", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onboardingDone || settings.retentionForever) return Result.success()
        var status = retention.status()
        val cutoff = status.cutoffMillis ?: return Result.success()

        if (status.expiresSoon && prefs.getLong("warned_cutoff", 0L) != cutoff) {
            notifications.notify(
                NotificationChannels.DATA, 3005, "Data lama akan melewati masa retensi",
                "Sebagian transaksi akan melewati masa penyimpanan ${status.retentionLabel} dalam 30 hari. " +
                    "DuitKu akan membuat arsip dan backup lebih dulu; tidak ada data yang dihapus tanpa persetujuan Anda.",
            )
            prefs.edit().putLong("warned_cutoff", cutoff).apply()
        }

        if (status.hasExpiredData && !status.prepared) {
            when (val result = retention.prepare()) {
                is PrepareResult.Ready -> {
                    status = retention.status()
                    notifications.notify(
                        NotificationChannels.DATA, 3006,
                        if (settings.retentionAction == RetentionAction.ARCHIVE_ONLY) "Data lama diarsipkan" else "Arsip siap, menunggu konfirmasi",
                        if (settings.retentionAction == RetentionAction.ARCHIVE_ONLY) {
                            "${result.transactionCount} transaksi lama sudah diarsipkan dan di-backup. Tidak ada yang dihapus."
                        } else {
                            "${result.transactionCount} transaksi lama sudah diarsipkan dan di-backup. Buka Pengaturan > Retensi Data untuk membersihkan detailnya."
                        },
                    )
                }
                is PrepareResult.Failed -> notifications.notify(NotificationChannels.DATA, 3007, "Persiapan arsip gagal", result.reason)
                PrepareResult.NothingToDo -> Unit
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "retention-check"
    }
}

/** Pengingat harian untuk mencatat transaksi (dilewati bila hari ini sudah ada catatan). */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val transactions: TransactionRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onboardingDone || !settings.dailyReminderEnabled) return Result.success()
        val today = LocalDate.now()
        val todayCount = transactions.observeFiltered(
            TransactionFilter(fromMillis = today.startOfDayMillis(), toMillis = today.endOfDayMillis()),
        ).first().size
        if (todayCount == 0) {
            notifications.notify(
                NotificationChannels.REMINDER, 3008, "Sudah catat transaksi hari ini?",
                "Belum ada catatan untuk ${DateFormats.dayHeader(today)}. Catat sekarang agar laporan bulanan tetap akurat.",
            )
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily-reminder"
    }
}

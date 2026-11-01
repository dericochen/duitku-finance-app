package com.duitku.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duitku.app.core.notification.NotificationChannels
import com.duitku.app.core.notification.NotificationHelper
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Menutup otomatis periode yang sudah berakhir (dengan masa tenggang [GRACE_DAYS] hari supaya
 * transaksi yang terlambat dicatat masih bisa dimasukkan). Idempoten: periode yang sudah diarsipkan dilewati.
 * Bila perangkat mati beberapa hari, semua periode yang terlewat ditutup berurutan saat worker berjalan lagi.
 */
@HiltWorker
class AutoCloseMonthWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val statements: StatementRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onboardingDone || !settings.autoCloseMonthEnabled) return Result.success()

        val closed = mutableListOf<Int>()
        for (period in statements.periodsDueForAutoClose(GRACE_DAYS)) {
            if (statements.close(period) is CloseResult.Closed) closed += period
        }
        if (closed.isNotEmpty()) {
            val names = closed.joinToString { DateFormats.monthYear(it.toYearMonth()) }
            notifications.notify(
                NotificationChannels.DATA, NOTIFICATION_ID, "Mutasi bulanan ditutup",
                "$names sudah diarsipkan. Buka Arsip Mutasi untuk melihat atau mengekspor PDF.",
            )
        }
        return Result.success()
    }

    companion object {
        const val GRACE_DAYS = 3
        const val UNIQUE_NAME = "auto-close-month"
        private const val NOTIFICATION_ID = 3001
    }
}

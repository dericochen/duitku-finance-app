package com.duitku.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.BackupFrequency
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Pusat penjadwalan pekerjaan latar belakang. Dipanggil saat aplikasi dibuka dan saat pengaturan berubah. */
object WorkScheduler {

    fun scheduleDefaults(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            AutoCloseMonthWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoCloseMonthWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            RecurringWorker.UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            DebtReminderWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            RetentionWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build(),
        )
        // Catch-up saat aplikasi dibuka: jadwal yang terlewat saat perangkat mati langsung diproses.
        wm.enqueueUniqueWork(
            RecurringWorker.UNIQUE_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RecurringWorker>().build(),
        )
    }

    /** Menyesuaikan jadwal yang bergantung pada pengaturan (backup otomatis & pengingat harian). */
    fun applySettings(context: Context, settings: AppSettings) {
        applyBackup(context, settings.backupFrequency)
        applyReminder(context, settings.dailyReminderEnabled, settings.reminderHour, settings.reminderMinute)
    }

    fun applyBackup(context: Context, frequency: BackupFrequency) {
        val wm = WorkManager.getInstance(context)
        val days = when (frequency) {
            BackupFrequency.OFF -> {
                wm.cancelUniqueWork(AutoBackupWorker.UNIQUE_NAME)
                return
            }
            BackupFrequency.DAILY -> 1L
            BackupFrequency.WEEKLY -> 7L
            BackupFrequency.MONTHLY -> 30L
        }
        wm.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS).build(),
        )
    }

    fun applyReminder(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(DailyReminderWorker.UNIQUE_NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(
            DailyReminderWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** Milidetik sampai jam:menit berikutnya (hari ini bila belum lewat, selain itu besok). */
    fun delayUntil(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}

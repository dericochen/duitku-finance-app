package com.duitku.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duitku.app.core.notification.NotificationChannels
import com.duitku.app.core.notification.NotificationHelper
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.repository.DebtRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.planning.DebtReminderPolicy
import com.duitku.app.domain.usecase.planning.RunDueRecurringUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Mencatat transaksi berulang yang jatuh tempo, termasuk yang terlewat saat perangkat mati. */
@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runDue: RunDueRecurringUseCase,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.current().onboardingDone) return Result.success()
        val result = runDue()
        if (!result.isEmpty) {
            val parts = buildList {
                if (result.created > 0) add("${result.created} transaksi berulang dicatat otomatis")
                if (result.skippedClosed > 0) add("${result.skippedClosed} dilewati karena bulannya sudah ditutup")
                if (result.failed > 0) add("${result.failed} gagal (periksa dompet/kategori)")
            }
            notifications.notify(NotificationChannels.RECURRING, 3002, "Transaksi berulang", parts.joinToString(". ") + ".")
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "recurring-periodic"
        const val UNIQUE_NOW = "recurring-now"
    }
}

/** Pengingat jatuh tempo utang & piutang (H-3, H-1, hari H, lalu berkala saat terlambat). */
@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val debts: DebtRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("debt_reminders", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onboardingDone) return Result.success()
        val today = LocalDate.now()

        for (debt in debts.getActiveWithDueDate()) {
            val due = debt.dueDate?.toLocalDate() ?: continue
            val level = DebtReminderPolicy.levelFor(ChronoUnit.DAYS.between(today, due))
            val lastLevel = if (prefs.contains("level_${debt.id}")) prefs.getInt("level_${debt.id}", 0) else null
            val lastDay = if (prefs.contains("day_${debt.id}")) prefs.getLong("day_${debt.id}", 0) else null
            if (!DebtReminderPolicy.shouldNotify(level, lastLevel, lastDay, today.toEpochDay())) continue

            val paid = debts.get(debt.id)?.paidAmount ?: 0L
            val remaining = (debt.principalAmount - paid).coerceAtLeast(0)
            val who = if (debt.kind == DebtKind.DEBT) "Utang ke ${debt.partyName}" else "Piutang dari ${debt.partyName}"
            val whenText = when (level) {
                3 -> "jatuh tempo 3 hari lagi"
                1 -> "jatuh tempo besok"
                0 -> "jatuh tempo hari ini"
                else -> "sudah lewat jatuh tempo"
            }
            notifications.notify(
                NotificationChannels.DUE, 5000 + (debt.id % 1000).toInt(), "$who $whenText",
                "Sisa ${MoneyFormatter.format(remaining, settings.currencyCode)}.",
            )
            prefs.edit().putInt("level_${debt.id}", level ?: 0).putLong("day_${debt.id}", today.toEpochDay()).apply()
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "debt-reminder"
    }
}

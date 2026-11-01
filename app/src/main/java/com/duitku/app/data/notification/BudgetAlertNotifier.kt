package com.duitku.app.data.notification

import android.content.Context
import com.duitku.app.core.notification.NotificationChannels
import com.duitku.app.core.notification.NotificationHelper
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.repository.BudgetAlerts
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.budget.BudgetAlertPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Mengirim notifikasi saat pemakaian anggaran mencapai 80% dan 100%.
 * Level yang sudah diberitahukan disimpan per (periode, kategori) agar tidak berulang;
 * bila pemakaian turun kembali (transaksi dihapus/diubah) level direset.
 */
@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext context: Context,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationHelper,
) : BudgetAlerts {

    private val prefs = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)

    override suspend fun check(occurredAt: Long) {
        val settings = settingsRepository.current()
        val period = PeriodCalculator.periodOfMillis(occurredAt, settings.monthStartDay).toKey()
        val lines = budgets.getLines(period)
        if (lines.isEmpty()) return
        val names = categories.observeAll().first().associate { it.id to it.name }

        for (line in lines) {
            val level = BudgetAlertPolicy.levelOf(line.percent)
            val key = "p${period}_c${line.budget.categoryId ?: 0}"
            val notified = prefs.getInt(key, 0)
            if (BudgetAlertPolicy.shouldNotify(notified, level) && settings.budgetNotificationsEnabled) {
                send(line, level, period, names[line.budget.categoryId], settings.currencyCode)
            }
            if (level != notified) prefs.edit().putInt(key, level).apply()
        }
    }

    private fun send(line: BudgetLine, level: Int, period: Int, categoryName: String?, currency: String) {
        val label = if (line.isTotal) "Anggaran total" else "Anggaran ${categoryName ?: "kategori"}"
        val month = DateFormats.monthYear(period.toYearMonth())
        val title = if (level >= 100) "$label habis" else "$label hampir habis"
        val text = "Terpakai ${line.percent}% (${MoneyFormatter.format(line.spent, currency)} dari " +
            "${MoneyFormatter.format(line.limit, currency)}) untuk $month."
        notifications.notify(
            NotificationChannels.BUDGET,
            notificationId = 4000 + (period % 1000) * 100 + ((line.budget.categoryId ?: 0L) % 100).toInt(),
            title = title,
            text = text,
        )
    }
}

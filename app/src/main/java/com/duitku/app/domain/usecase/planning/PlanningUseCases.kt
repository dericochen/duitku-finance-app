package com.duitku.app.domain.usecase.planning

import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.core.util.toLocalDateTime
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.RecurringRunResult
import com.duitku.app.domain.model.RecurringTransaction
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.DebtRepository
import com.duitku.app.domain.repository.GoalRepository
import com.duitku.app.domain.repository.RecurringRepository
import com.duitku.app.domain.usecase.transaction.SaveTransactionResult
import com.duitku.app.domain.usecase.transaction.SaveTransactionUseCase
import java.time.LocalDateTime
import javax.inject.Inject

class SaveRecurringUseCase @Inject constructor(
    private val repository: RecurringRepository,
) {
    suspend operator fun invoke(rule: RecurringTransaction): UseCaseResult<Long> {
        val title = rule.title.trim()
        if (title.isEmpty()) return UseCaseResult.Failure("Nama transaksi berulang wajib diisi")
        if (title.length > 40) return UseCaseResult.Failure("Nama maksimal 40 karakter")
        if (rule.amount <= 0) return UseCaseResult.Failure("Nominal harus lebih dari 0")
        if (rule.amount > TransactionRules.MAX_AMOUNT) return UseCaseResult.Failure("Nominal terlalu besar")
        if (rule.walletId <= 0) return UseCaseResult.Failure("Pilih dompet")
        if (rule.intervalCount !in 1..99) return UseCaseResult.Failure("Interval harus antara 1 dan 99")
        if (rule.type == TransactionType.TRANSFER) {
            val to = rule.toWalletId
            if (to == null || to <= 0) return UseCaseResult.Failure("Pilih dompet tujuan")
            if (to == rule.walletId) return UseCaseResult.Failure("Dompet asal dan tujuan tidak boleh sama")
        } else if (rule.categoryId == null) {
            return UseCaseResult.Failure("Pilih kategori")
        }
        if (rule.endAt != null && rule.endAt < rule.startAt) return UseCaseResult.Failure("Tanggal berakhir sebelum tanggal mulai")
        return try {
            UseCaseResult.Success(
                repository.save(
                    rule.copy(
                        title = title,
                        tags = TransactionRules.normalizeTags(rule.tags),
                        toWalletId = if (rule.type == TransactionType.TRANSFER) rule.toWalletId else null,
                        categoryId = if (rule.type == TransactionType.TRANSFER) null else rule.categoryId,
                        subCategoryId = if (rule.type == TransactionType.TRANSFER) null else rule.subCategoryId,
                    ),
                ),
            )
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Gagal menyimpan")
        }
    }
}

/**
 * Menjalankan semua transaksi berulang yang jatuh tempo, termasuk yang terlewat (perangkat mati / aplikasi
 * lama tidak dibuka). Kemajuan jadwal disimpan setelah tiap kemunculan sehingga aman diulang (idempoten).
 */
class RunDueRecurringUseCase @Inject constructor(
    private val repository: RecurringRepository,
    private val saveTransaction: SaveTransactionUseCase,
) {
    suspend operator fun invoke(now: LocalDateTime = LocalDateTime.now()): RecurringRunResult {
        var created = 0
        var skipped = 0
        var failed = 0
        val nowMillis = now.toEpochMillis()

        for (rule in repository.getDue(nowMillis)) {
            val start = rule.startAt.toLocalDateTime()
            val end = rule.endAt?.toLocalDateTime()
            val occurrences = RecurrenceCalculator.dueOccurrences(
                start, rule.frequency, rule.intervalCount, rule.nextDueAt.toLocalDateTime(), now, end,
            )
            var next = rule.nextDueAt
            for (occ in occurrences) {
                val tx = Transaction(
                    type = rule.type, amount = rule.amount, walletId = rule.walletId, toWalletId = rule.toWalletId,
                    categoryId = rule.categoryId, subCategoryId = rule.subCategoryId,
                    note = rule.note.ifBlank { rule.title }, tags = rule.tags,
                    occurredAt = occ.at.toEpochMillis(), recurringId = rule.id,
                )
                when (saveTransaction(tx)) {
                    is SaveTransactionResult.Success -> created++
                    is SaveTransactionResult.PeriodClosed -> skipped++
                    is SaveTransactionResult.Invalid, is SaveTransactionResult.Failed -> failed++
                }
                next = RecurrenceCalculator.occurrence(start, rule.frequency, rule.intervalCount, occ.index + 1).toEpochMillis()
                repository.advance(rule.id, next, nowMillis, deactivate = false)
            }
            if (occurrences.isEmpty()) continue
            // Selesai bila jadwal berikutnya melewati tanggal berakhir.
            if (rule.endAt != null && next > rule.endAt) repository.advance(rule.id, next, nowMillis, deactivate = true)
        }
        return RecurringRunResult(created, skipped, failed)
    }
}

class SaveGoalUseCase @Inject constructor(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(goal: SavingGoal): UseCaseResult<Long> {
        if (goal.name.isBlank()) return UseCaseResult.Failure("Nama target wajib diisi")
        if (goal.name.trim().length > 40) return UseCaseResult.Failure("Nama maksimal 40 karakter")
        if (goal.targetAmount <= 0) return UseCaseResult.Failure("Nominal target harus lebih dari 0")
        if (goal.targetAmount > TransactionRules.MAX_AMOUNT) return UseCaseResult.Failure("Nominal target terlalu besar")
        return UseCaseResult.Success(repository.save(goal.copy(name = goal.name.trim())))
    }
}

class AddGoalContributionUseCase @Inject constructor(
    private val repository: GoalRepository,
) {
    /** @param amount positif = setoran, negatif = penarikan. */
    suspend operator fun invoke(goal: SavingGoal, amount: Long, note: String, at: Long): UseCaseResult<SavingGoal> {
        if (amount == 0L) return UseCaseResult.Failure("Nominal tidak boleh 0")
        if (kotlin.math.abs(amount) > TransactionRules.MAX_AMOUNT) return UseCaseResult.Failure("Nominal terlalu besar")
        if (amount < 0 && -amount > goal.savedAmount) return UseCaseResult.Failure("Penarikan melebihi dana yang terkumpul")
        val updated = repository.addContribution(goal.id, amount, note.trim().take(100), at)
            ?: return UseCaseResult.Failure("Target tidak ditemukan")
        return UseCaseResult.Success(updated)
    }
}

class SaveDebtUseCase @Inject constructor(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(debt: Debt): UseCaseResult<Long> {
        if (debt.partyName.isBlank()) return UseCaseResult.Failure("Nama pihak wajib diisi")
        if (debt.partyName.trim().length > 40) return UseCaseResult.Failure("Nama maksimal 40 karakter")
        if (debt.principalAmount <= 0) return UseCaseResult.Failure("Nominal harus lebih dari 0")
        if (debt.principalAmount > TransactionRules.MAX_AMOUNT) return UseCaseResult.Failure("Nominal terlalu besar")
        return UseCaseResult.Success(repository.save(debt.copy(partyName = debt.partyName.trim(), note = debt.note.trim().take(200))))
    }
}

class AddDebtPaymentUseCase @Inject constructor(
    private val repository: DebtRepository,
) {
    suspend operator fun invoke(item: DebtWithPaid, amount: Long, note: String, at: Long): UseCaseResult<DebtWithPaid> {
        if (amount <= 0) return UseCaseResult.Failure("Nominal harus lebih dari 0")
        if (item.debt.isPaidOff) return UseCaseResult.Failure("Catatan ini sudah lunas")
        if (amount > item.remaining) return UseCaseResult.Failure("Pembayaran melebihi sisa (${item.remaining})")
        val updated = repository.addPayment(item.debt.id, amount, note.trim().take(100), at)
            ?: return UseCaseResult.Failure("Catatan tidak ditemukan")
        return UseCaseResult.Success(updated)
    }
}

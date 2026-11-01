package com.duitku.app.data.repository

import androidx.room.withTransaction
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.DebtDao
import com.duitku.app.data.local.dao.RecurringTransactionDao
import com.duitku.app.data.local.dao.SavingGoalDao
import com.duitku.app.data.local.entity.DebtEntity
import com.duitku.app.data.local.entity.DebtPaymentEntity
import com.duitku.app.data.local.entity.GoalContributionEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import com.duitku.app.data.local.entity.SavingGoalEntity
import com.duitku.app.core.util.toLocalDateTime
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtPayment
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.GoalContribution
import com.duitku.app.domain.model.RecurringTransaction
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.domain.repository.DebtRepository
import com.duitku.app.domain.repository.GoalRepository
import com.duitku.app.domain.repository.RecurringRepository
import com.duitku.app.domain.usecase.planning.RecurrenceCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.duitku.app.data.local.dao.DebtWithPaid as DebtWithPaidRow

@Singleton
class RecurringRepositoryImpl @Inject constructor(
    private val dao: RecurringTransactionDao,
) : RecurringRepository {

    override fun observeAll(): Flow<List<RecurringTransaction>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun get(id: Long) = dao.getById(id)?.toDomain()

    override suspend fun getDue(nowMillis: Long) = dao.getDue(nowMillis).map { it.toDomain() }

    override suspend fun save(rule: RecurringTransaction): Long {
        val existing = if (rule.id != 0L) dao.getById(rule.id) else null
        // Aturan baru atau yang jadwalnya diubah tidak mengisi mundur: mulai dari kemunculan pertama >= sekarang.
        val scheduleChanged = existing == null || existing.startAt != rule.startAt ||
            existing.frequency != rule.frequency || existing.intervalCount != rule.intervalCount
        val nextDue = if (scheduleChanged) {
            val start = rule.startAt.toLocalDateTime()
            RecurrenceCalculator.firstAtOrAfter(
                start, rule.frequency, rule.intervalCount, maxOf(rule.startAt, System.currentTimeMillis()).toLocalDateTime(),
            ).at.let { at -> at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        } else {
            existing!!.nextDueAt
        }
        val entity = rule.toEntity(createdAt = existing?.createdAt ?: System.currentTimeMillis()).copy(
            nextDueAt = nextDue, lastRunAt = existing?.lastRunAt,
        )
        return if (existing == null) dao.insert(entity.copy(id = 0)) else {
            dao.update(entity)
            entity.id
        }
    }

    override suspend fun setActive(id: Long, active: Boolean) {
        val e = dao.getById(id) ?: return
        // Mengaktifkan kembali tidak boleh membanjiri transaksi masa lalu: geser jadwal ke kemunculan berikutnya.
        val next = if (active && e.nextDueAt < System.currentTimeMillis()) {
            RecurrenceCalculator.firstAtOrAfter(e.startAt.toLocalDateTime(), e.frequency, e.intervalCount, java.time.LocalDateTime.now())
                .at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            e.nextDueAt
        }
        dao.update(e.copy(isActive = active, nextDueAt = next))
    }

    override suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun advance(id: Long, nextDueAt: Long, lastRunAt: Long, deactivate: Boolean) {
        val e = dao.getById(id) ?: return
        dao.update(e.copy(nextDueAt = nextDueAt, lastRunAt = lastRunAt, isActive = e.isActive && !deactivate))
    }

    private fun RecurringTransactionEntity.toDomain() = RecurringTransaction(
        id, title, type, amount, walletId, toWalletId, categoryId, subCategoryId, note, tags, frequency,
        intervalCount, startAt, endAt, nextDueAt, lastRunAt, isActive,
    )

    private fun RecurringTransaction.toEntity(createdAt: Long) = RecurringTransactionEntity(
        id, title, type, amount, walletId, toWalletId, categoryId, subCategoryId, note, tags, frequency,
        intervalCount, startAt, endAt, nextDueAt, lastRunAt, isActive, createdAt,
    )
}

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val database: DuitKuDatabase,
    private val dao: SavingGoalDao,
) : GoalRepository {

    override fun observeGoals(): Flow<List<SavingGoal>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeContributions(goalId: Long): Flow<List<GoalContribution>> =
        dao.observeContributions(goalId).map { l -> l.map { it.toDomain() } }

    override suspend fun getContributions(goalId: Long) = dao.getContributions(goalId).map { it.toDomain() }

    override suspend fun save(goal: SavingGoal): Long {
        val existing = if (goal.id != 0L) dao.getById(goal.id) else null
        return if (existing == null) {
            dao.insert(goal.toEntity(createdAt = System.currentTimeMillis()).copy(id = 0, savedAmount = 0, completedAt = null))
        } else {
            // savedAmount hanya diubah lewat setoran agar riwayat selalu cocok dengan total.
            val updated = existing.copy(
                name = goal.name, targetAmount = goal.targetAmount, deadline = goal.deadline,
                iconKey = goal.iconKey, colorArgb = goal.colorArgb, note = goal.note,
            )
            dao.update(updated.copy(completedAt = completionOf(updated, existing.completedAt)))
            existing.id
        }
    }

    override suspend fun delete(goalId: Long) {
        dao.getById(goalId)?.let { dao.delete(it) }
    }

    override suspend fun addContribution(goalId: Long, amount: Long, note: String, at: Long): SavingGoal? =
        database.withTransaction {
            val goal = dao.getById(goalId) ?: return@withTransaction null
            val newSaved = (goal.savedAmount + amount).coerceAtLeast(0)
            dao.insertContribution(GoalContributionEntity(goalId = goalId, amount = amount, contributedAt = at, note = note))
            val updated = goal.copy(savedAmount = newSaved)
            dao.update(updated.copy(completedAt = completionOf(updated, goal.completedAt)))
            dao.getById(goalId)?.toDomain()
        }

    private fun completionOf(goal: SavingGoalEntity, previous: Long?): Long? =
        if (goal.savedAmount >= goal.targetAmount) previous ?: System.currentTimeMillis() else null

    private fun SavingGoalEntity.toDomain() = SavingGoal(
        id, name, targetAmount, savedAmount, deadline, iconKey, colorArgb, note, createdAt, completedAt,
    )

    private fun SavingGoal.toEntity(createdAt: Long) = SavingGoalEntity(
        id, name, targetAmount, savedAmount, deadline, iconKey, colorArgb, note, createdAt, completedAt,
    )

    private fun GoalContributionEntity.toDomain() = GoalContribution(id, goalId, amount, contributedAt, note)
}

@Singleton
class DebtRepositoryImpl @Inject constructor(
    private val database: DuitKuDatabase,
    private val dao: DebtDao,
) : DebtRepository {

    override fun observeAll(): Flow<List<DebtWithPaid>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observePayments(debtId: Long): Flow<List<DebtPayment>> =
        dao.observePayments(debtId).map { l -> l.map { DebtPayment(it.id, it.debtId, it.amount, it.paidAt, it.note) } }

    override suspend fun get(id: Long) = dao.getWithPaid(id)?.toDomain()

    override suspend fun getActiveWithDueDate() = dao.getActiveWithDueDate().map { it.toDomain() }

    override suspend fun save(debt: Debt): Long {
        val existing = if (debt.id != 0L) dao.getById(debt.id) else null
        return if (existing == null) {
            dao.insert(debt.toEntity(System.currentTimeMillis()).copy(id = 0, isPaidOff = false, paidOffAt = null))
        } else {
            dao.update(
                existing.copy(
                    kind = debt.kind, partyName = debt.partyName, principalAmount = debt.principalAmount,
                    dueDate = debt.dueDate, note = debt.note, reminderEnabled = debt.reminderEnabled,
                ),
            )
            existing.id
        }
    }

    override suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun addPayment(debtId: Long, amount: Long, note: String, at: Long): DebtWithPaid? =
        database.withTransaction {
            val row = dao.getWithPaid(debtId) ?: return@withTransaction null
            dao.insertPayment(DebtPaymentEntity(debtId = debtId, amount = amount, paidAt = at, note = note))
            val paid = row.paidAmount + amount
            if (paid >= row.debt.principalAmount && !row.debt.isPaidOff) {
                dao.update(row.debt.copy(isPaidOff = true, paidOffAt = at))
            }
            dao.getWithPaid(debtId)?.toDomain()
        }

    override suspend fun markPaidOff(id: Long) {
        val d = dao.getById(id) ?: return
        dao.update(d.copy(isPaidOff = true, paidOffAt = System.currentTimeMillis()))
    }

    private fun DebtRowAlias.toDomain() = DebtWithPaid(debt.toDomain(), paidAmount)
    private fun DebtEntity.toDomain() = Debt(
        id, kind, partyName, principalAmount, dueDate, note, isPaidOff, reminderEnabled, createdAt, paidOffAt,
    )

    private fun Debt.toEntity(createdAt: Long) = DebtEntity(
        id, kind, partyName, principalAmount, dueDate, note, isPaidOff, reminderEnabled, createdAt, paidOffAt,
    )
}

private typealias DebtRowAlias = DebtWithPaidRow

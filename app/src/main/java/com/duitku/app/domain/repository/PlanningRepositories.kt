package com.duitku.app.domain.repository

import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtPayment
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.GoalContribution
import com.duitku.app.domain.model.RecurringTransaction
import com.duitku.app.domain.model.SavingGoal
import kotlinx.coroutines.flow.Flow

interface RecurringRepository {
    fun observeAll(): Flow<List<RecurringTransaction>>
    suspend fun get(id: Long): RecurringTransaction?
    suspend fun getDue(nowMillis: Long): List<RecurringTransaction>
    suspend fun save(rule: RecurringTransaction): Long
    suspend fun setActive(id: Long, active: Boolean)
    suspend fun delete(id: Long)

    /** Menyimpan kemajuan jadwal setelah satu kemunculan diproses (agar tidak diproses dua kali). */
    suspend fun advance(id: Long, nextDueAt: Long, lastRunAt: Long, deactivate: Boolean)
}

interface GoalRepository {
    fun observeGoals(): Flow<List<SavingGoal>>
    fun observeContributions(goalId: Long): Flow<List<GoalContribution>>
    suspend fun getContributions(goalId: Long): List<GoalContribution>
    suspend fun save(goal: SavingGoal): Long
    suspend fun delete(goalId: Long)

    /** Menambah (positif) atau menarik (negatif) dana. Mengembalikan target terbaru. */
    suspend fun addContribution(goalId: Long, amount: Long, note: String, at: Long): SavingGoal?
}

interface DebtRepository {
    fun observeAll(): Flow<List<DebtWithPaid>>
    fun observePayments(debtId: Long): Flow<List<DebtPayment>>
    suspend fun get(id: Long): DebtWithPaid?
    suspend fun getActiveWithDueDate(): List<Debt>
    suspend fun save(debt: Debt): Long
    suspend fun delete(id: Long)

    /** Mencatat pembayaran; otomatis lunas bila total pembayaran mencapai pokok. */
    suspend fun addPayment(debtId: Long, amount: Long, note: String, at: Long): DebtWithPaid?
    suspend fun markPaidOff(id: Long)
}

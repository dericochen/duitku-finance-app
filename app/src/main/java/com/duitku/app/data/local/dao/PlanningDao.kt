package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.duitku.app.data.local.entity.DebtEntity
import com.duitku.app.data.local.entity.DebtPaymentEntity
import com.duitku.app.data.local.entity.GoalContributionEntity
import com.duitku.app.data.local.entity.SavingGoalEntity
import kotlinx.coroutines.flow.Flow

/** Utang/piutang beserta total pembayaran yang sudah masuk. */
data class DebtWithPaid(
    @Embedded val debt: DebtEntity,
    val paidAmount: Long,
)

@Dao
interface SavingGoalDao {
    @Insert
    suspend fun insert(entity: SavingGoalEntity): Long

    @Update
    suspend fun update(entity: SavingGoalEntity)

    @Delete
    suspend fun delete(entity: SavingGoalEntity)

    @Query("SELECT * FROM saving_goals ORDER BY completedAt IS NOT NULL, deadline IS NULL, deadline ASC, id ASC")
    fun observeAll(): Flow<List<SavingGoalEntity>>

    @Query("SELECT * FROM saving_goals ORDER BY completedAt IS NOT NULL, deadline IS NULL, deadline ASC, id ASC")
    suspend fun getAll(): List<SavingGoalEntity>

    @Query("SELECT * FROM saving_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingGoalEntity?

    @Query("SELECT * FROM saving_goals WHERE id = :id")
    fun observeById(id: Long): Flow<SavingGoalEntity?>

    @Insert
    suspend fun insertContribution(entity: GoalContributionEntity): Long

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY contributedAt DESC, id DESC")
    fun observeContributions(goalId: Long): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY contributedAt ASC, id ASC")
    suspend fun getContributions(goalId: Long): List<GoalContributionEntity>

    @Query("DELETE FROM saving_goals")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Insert
    suspend fun insert(entity: DebtEntity): Long

    @Update
    suspend fun update(entity: DebtEntity)

    @Delete
    suspend fun delete(entity: DebtEntity)

    @Query(
        """SELECT d.*, COALESCE((SELECT SUM(p.amount) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidAmount
           FROM debts d ORDER BY d.isPaidOff ASC, d.dueDate IS NULL, d.dueDate ASC, d.id DESC""",
    )
    fun observeAll(): Flow<List<DebtWithPaid>>

    @Query(
        """SELECT d.*, COALESCE((SELECT SUM(p.amount) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidAmount
           FROM debts d ORDER BY d.isPaidOff ASC, d.dueDate IS NULL, d.dueDate ASC, d.id DESC""",
    )
    suspend fun getAll(): List<DebtWithPaid>

    @Query(
        """SELECT d.*, COALESCE((SELECT SUM(p.amount) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidAmount
           FROM debts d WHERE d.id = :id""",
    )
    suspend fun getWithPaid(id: Long): DebtWithPaid?

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    @Insert
    suspend fun insertPayment(entity: DebtPaymentEntity): Long

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY paidAt DESC, id DESC")
    fun observePayments(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY paidAt ASC, id ASC")
    suspend fun getPayments(debtId: Long): List<DebtPaymentEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_payments WHERE debtId = :debtId")
    suspend fun getPaidAmount(debtId: Long): Long

    /** Utang/piutang aktif yang punya jatuh tempo, untuk reminder. */
    @Query("SELECT * FROM debts WHERE isPaidOff = 0 AND reminderEnabled = 1 AND dueDate IS NOT NULL")
    suspend fun getActiveWithDueDate(): List<DebtEntity>

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}

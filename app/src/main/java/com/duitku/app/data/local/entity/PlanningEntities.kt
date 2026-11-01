package com.duitku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.duitku.app.domain.model.DebtKind

@Serializable
@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val savedAmount: Long = 0,
    val deadline: Long? = null,
    val iconKey: String,
    val colorArgb: Int,
    val note: String = "",
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(SavingGoalEntity::class, ["id"], ["goalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("goalId"), Index("contributedAt")],
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Positif = setoran, negatif = penarikan. */
    val amount: Long,
    val contributedAt: Long,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "debts",
    indices = [Index("dueDate"), Index("isPaidOff")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: DebtKind,
    val partyName: String,
    val principalAmount: Long,
    val dueDate: Long? = null,
    val note: String = "",
    val isPaidOff: Boolean = false,
    val reminderEnabled: Boolean = true,
    val createdAt: Long,
    val paidOffAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(DebtEntity::class, ["id"], ["debtId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("debtId"), Index("paidAt")],
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val paidAt: Long,
    val note: String = "",
)

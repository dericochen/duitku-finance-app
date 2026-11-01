package com.duitku.app.domain.usecase.statement

import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.SnapBudget
import com.duitku.app.domain.model.SnapCategory
import com.duitku.app.domain.model.SnapDebt
import com.duitku.app.domain.model.SnapGoal
import com.duitku.app.domain.model.SnapPrevious
import com.duitku.app.domain.model.SnapRecurring
import com.duitku.app.domain.model.SnapTransaction
import com.duitku.app.domain.model.SnapWallet
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.model.Transaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bahan baku snapshot; dirakit oleh layer data dari database. */
data class SnapshotInput(
    val periodYm: Int,
    val startMillis: Long,
    val endMillis: Long,
    val startDay: Int,
    val currency: String,
    val owner: String,
    val wallets: List<SnapWallet>,
    val categories: List<Category>,
    /** Transaksi dalam periode, urut waktu naik. */
    val transactions: List<Transaction>,
    val budgets: List<SnapBudget> = emptyList(),
    val goals: List<SnapGoal> = emptyList(),
    val debts: List<SnapDebt> = emptyList(),
    val recurring: List<SnapRecurring> = emptyList(),
    val previous: SnapPrevious = SnapPrevious(),
)

object SnapshotFactory {
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun build(input: SnapshotInput): StatementSnapshot {
        val totals = PeriodMath.totals(input.transactions, input.wallets)
        return StatementSnapshot(
            periodYm = input.periodYm,
            startMillis = input.startMillis,
            endMillis = input.endMillis,
            startDay = input.startDay,
            currency = input.currency,
            owner = input.owner,
            totals = totals,
            wallets = input.wallets,
            categories = input.categories.map { SnapCategory(it.id, it.name, it.kind, it.parentId) },
            transactions = input.transactions.map {
                SnapTransaction(
                    id = it.id, type = it.type, amount = it.amount, walletId = it.walletId, toWalletId = it.toWalletId,
                    categoryId = it.categoryId, subCategoryId = it.subCategoryId, note = it.note, tags = it.tags,
                    occurredAt = it.occurredAt, recurring = it.recurringId != null,
                )
            },
            expenseByCategory = PeriodMath.categoryTotals(input.transactions, CategoryKind.EXPENSE),
            incomeByCategory = PeriodMath.categoryTotals(input.transactions, CategoryKind.INCOME),
            budgets = input.budgets,
            goals = input.goals,
            debts = input.debts,
            recurring = input.recurring,
            previous = input.previous,
        )
    }

    fun encode(snapshot: StatementSnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): StatementSnapshot = json.decodeFromString(text)
}

package com.duitku.app.domain.usecase.budget

import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.BudgetRepository
import javax.inject.Inject

class SaveBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget): UseCaseResult<Long> {
        if (budget.amount <= 0) return UseCaseResult.Failure("Nominal anggaran harus lebih dari 0")
        if (budget.amount > TransactionRules.MAX_AMOUNT) return UseCaseResult.Failure("Nominal anggaran terlalu besar")
        return try {
            UseCaseResult.Success(repository.save(budget))
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Gagal menyimpan anggaran")
        }
    }
}

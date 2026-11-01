package com.duitku.app.domain.usecase.setup

import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.repository.DemoDataLoader
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import com.duitku.app.domain.usecase.wallet.SaveWalletUseCase
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingInput(
    val ownerName: String,
    val firstWallet: Wallet,
    /** Kunci kategori bawaan terpilih (lihat [SeedDefaultCategoriesUseCase.key]). */
    val selectedCategoryKeys: Set<String>,
    val monthStartDay: Int,
    val retentionMonths: Int,
    val loadDemoData: Boolean,
)

class CompleteOnboardingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val saveWallet: SaveWalletUseCase,
    private val seedCategories: SeedDefaultCategoriesUseCase,
    private val demoDataLoader: DemoDataLoader,
) {
    suspend operator fun invoke(input: OnboardingInput): UseCaseResult<Unit> {
        // Saldo awal dompet pertama dianggap sudah ada sebelum periode berjalan, jadi masuk ke saldo awal periode ini.
        val startDay = input.monthStartDay.coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY)
        val currentPeriod = PeriodCalculator.rangeOf(PeriodCalculator.periodOf(LocalDate.now(), startDay), startDay)
        val walletResult = saveWallet(input.firstWallet.copy(openedAt = currentPeriod.startMillis() - 1))
        if (walletResult is UseCaseResult.Failure) return walletResult

        return try {
            seedCategories(input.selectedCategoryKeys)
            if (input.loadDemoData) demoDataLoader.load()
            settingsRepository.update {
                it.copy(
                    onboardingDone = true,
                    ownerName = input.ownerName.trim(),
                    monthStartDay = input.monthStartDay.coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY),
                    retentionMonths = input.retentionMonths,
                )
            }
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Gagal menyelesaikan pengaturan awal")
        }
    }
}

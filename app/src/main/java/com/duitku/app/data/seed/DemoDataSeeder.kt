package com.duitku.app.data.seed

import androidx.room.withTransaction
import com.duitku.app.core.util.startOfDayMillis
import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.core.util.toKey
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.BudgetDao
import com.duitku.app.data.local.dao.CategoryDao
import com.duitku.app.data.local.dao.DebtDao
import com.duitku.app.data.local.dao.RecurringTransactionDao
import com.duitku.app.data.local.dao.SavingGoalDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.dao.WalletDao
import com.duitku.app.data.local.entity.BudgetEntity
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.DebtEntity
import com.duitku.app.data.local.entity.DebtPaymentEntity
import com.duitku.app.data.local.entity.GoalContributionEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import com.duitku.app.data.local.entity.SavingGoalEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.DemoDataLoader
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Membuat data contoh (default 12 bulan) agar grafik, anggaran, dan ekspor PDF bisa langsung dicoba.
 * Hasilnya deterministik (seed acak tetap) sehingga mudah dibandingkan antar uji coba.
 * Semua dompet contoh diberi akhiran " (Contoh)" supaya mudah dikenali dan dihapus.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val database: DuitKuDatabase,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: SavingGoalDao,
    private val debtDao: DebtDao,
    private val recurringDao: RecurringTransactionDao,
    private val seedCategories: SeedDefaultCategoriesUseCase,
    private val settingsRepository: SettingsRepository,
) : DemoDataLoader {
    data class Result(val walletCount: Int, val transactionCount: Int)

    override suspend fun load(): Int = seed().transactionCount

    suspend fun seed(today: LocalDate = LocalDate.now(), months: Int = 12, randomSeed: Int = 42): Result {
        seedCategories(null)
        val categories = categoryDao.getAll()
        val rnd = Random(randomSeed)

        return database.withTransaction {
            val now = System.currentTimeMillis()
            // Saldo awal dompet contoh berlaku sebelum bulan pertama data contoh.
            val opened = YearMonth.from(today).minusMonths((months - 1).toLong()).atDay(1).startOfDayMillis() - 1
            val cash = insertWallet("Tunai (Contoh)", WalletType.CASH, 500_000, "cash", 0, now, opened)
            val bank = insertWallet("Bank (Contoh)", WalletType.BANK, 12_000_000, "bank", 3, now, opened)
            val ewallet = insertWallet("E-Wallet (Contoh)", WalletType.E_WALLET, 200_000, "ewallet", 2, now, opened)
            val card = insertWallet("Kartu Kredit (Contoh)", WalletType.CREDIT_CARD, 0, "card", 7, now, opened)

            val recurringIds = seedRecurring(categories, bank, card, now)
            val builder = Builder(categories, rnd, now, cash, bank, ewallet, card, today, recurringIds)
            val startMonth = YearMonth.from(today).minusMonths((months - 1).toLong())
            for (i in 0 until months) builder.addMonth(startMonth.plusMonths(i.toLong()), i)

            transactionDao.insertAll(builder.result)
            budgetDao.insertAll(demoBudgets(categories, startMonth, months))
            seedPlanning(today, now)
            settingsRepository.update { it.copy(demoDataLoaded = true) }
            Result(walletCount = 4, transactionCount = builder.result.size)
        }
    }

    /** Anggaran contoh untuk tiap bulan: total + beberapa kategori (Tagihan dengan rollover). */
    private fun demoBudgets(categories: List<CategoryEntity>, startMonth: YearMonth, months: Int): List<BudgetEntity> {
        val parents = categories.filter { it.parentId == null && it.kind == CategoryKind.EXPENSE }
        val perCategory = listOf("Makanan" to 2_000_000L, "Transport" to 600_000L, "Belanja" to 1_500_000L, "Hiburan" to 500_000L, "Tagihan" to 3_500_000L)
        val rows = mutableListOf<BudgetEntity>()
        for (i in 0 until months) {
            val ym = startMonth.plusMonths(i.toLong()).toKey()
            rows += BudgetEntity(periodYm = ym, categoryId = null, amount = 7_500_000)
            perCategory.forEach { (name, amount) ->
                parents.firstOrNull { it.name == name }?.let {
                    rows += BudgetEntity(periodYm = ym, categoryId = it.id, amount = amount, rollover = name == "Tagihan")
                }
            }
        }
        return rows
    }

    /** Aturan berulang contoh. Jadwal berikutnya di masa depan agar tidak menggandakan transaksi contoh. */
    private suspend fun seedRecurring(categories: List<CategoryEntity>, bank: Long, card: Long, now: Long): Map<String, Long> {
        fun cat(name: String, kind: CategoryKind) = categories.firstOrNull { it.parentId == null && it.name == name && it.kind == kind }?.id
        fun nextOnDay(day: Int): Long {
            val today = LocalDate.now()
            var d = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
            if (!d.isAfter(today)) d = d.plusMonths(1).let { it.withDayOfMonth(day.coerceAtMost(it.lengthOfMonth())) }
            return LocalDateTime.of(d, java.time.LocalTime.of(8, 0)).toEpochMillis()
        }
        suspend fun rule(key: String, title: String, type: TransactionType, amount: Long, wallet: Long, category: Long?, day: Int, startMonthsAgo: Long): Pair<String, Long> {
            val start = LocalDateTime.of(LocalDate.now().minusMonths(startMonthsAgo).withDayOfMonth(day.coerceAtMost(28)), java.time.LocalTime.of(8, 0))
            return key to recurringDao.insert(
                RecurringTransactionEntity(
                    title = title, type = type, amount = amount, walletId = wallet, categoryId = category,
                    frequency = RecurrenceFrequency.MONTHLY, startAt = start.toEpochMillis(), nextDueAt = nextOnDay(day), createdAt = now,
                ),
            )
        }
        return listOf(
            rule("Gaji", "Gaji bulanan", TransactionType.INCOME, 9_800_000, bank, cat("Gaji", CategoryKind.INCOME), 25, 11),
            rule("Sewa", "Sewa kontrakan", TransactionType.EXPENSE, 1_800_000, bank, cat("Tagihan", CategoryKind.EXPENSE), 1, 11),
            rule("Musik", "Langganan musik", TransactionType.EXPENSE, 54_000, card, cat("Hiburan", CategoryKind.EXPENSE), 10, 11),
            rule("Cicilan", "Cicilan perangkat", TransactionType.EXPENSE, 750_000, bank, cat("Cicilan", CategoryKind.EXPENSE), 15, 11),
        ).toMap()
    }

    /** Target tabungan, utang, dan piutang contoh. */
    private suspend fun seedPlanning(today: LocalDate, now: Long) {
        val day = 86_400_000L
        val emergency = goalDao.insert(
            SavingGoalEntity(name = "Dana darurat", targetAmount = 30_000_000, savedAmount = 9_000_000, deadline = now + 400 * day,
                iconKey = "savings", colorArgb = Palette.colors[0], createdAt = now - 300 * day),
        )
        for (i in 1..9) {
            goalDao.insertContribution(GoalContributionEntity(goalId = emergency, amount = 1_000_000, contributedAt = now - (300 - i * 30L) * day, note = "Setoran bulanan"))
        }
        val trip = goalDao.insert(
            SavingGoalEntity(name = "Liburan akhir tahun", targetAmount = 8_000_000, savedAmount = 2_500_000, deadline = now + 120 * day,
                iconKey = "travel", colorArgb = Palette.colors[3], createdAt = now - 90 * day),
        )
        goalDao.insertContribution(GoalContributionEntity(goalId = trip, amount = 1_500_000, contributedAt = now - 60 * day, note = "Bonus"))
        goalDao.insertContribution(GoalContributionEntity(goalId = trip, amount = 1_000_000, contributedAt = now - 20 * day))

        val budi = debtDao.insert(
            DebtEntity(kind = DebtKind.DEBT, partyName = "Budi", principalAmount = 2_000_000, dueDate = now + 10 * day,
                note = "Pinjaman darurat", createdAt = now - 40 * day),
        )
        debtDao.insertPayment(DebtPaymentEntity(debtId = budi, amount = 500_000, paidAt = now - 15 * day, note = "Cicilan 1"))
        debtDao.insert(
            DebtEntity(kind = DebtKind.RECEIVABLE, partyName = "Sari", principalAmount = 750_000, dueDate = now - 5 * day,
                note = "Patungan makan", createdAt = now - 30 * day),
        )
    }

    private suspend fun insertWallet(
        name: String,
        type: WalletType,
        initial: Long,
        icon: String,
        colorIndex: Int,
        now: Long,
        openedAt: Long,
    ): Long = walletDao.insert(
        WalletEntity(
            name = name,
            type = type,
            initialBalance = initial,
            iconKey = icon,
            colorArgb = Palette.colors[colorIndex],
            sortOrder = walletDao.nextSortOrder(),
            createdAt = now,
            openedAt = openedAt,
        ),
    )

    private class Builder(
        categories: List<CategoryEntity>,
        private val rnd: Random,
        private val now: Long,
        private val cash: Long,
        private val bank: Long,
        private val ewallet: Long,
        private val card: Long,
        private val today: LocalDate,
        private val recurringIds: Map<String, Long>,
    ) {
        val result = ArrayList<TransactionEntity>(1000)

        private val parents = categories.filter { it.parentId == null }
        private val subs = categories.filter { it.parentId != null }

        private fun parent(name: String, kind: CategoryKind) =
            parents.firstOrNull { it.name == name && it.kind == kind }

        private fun sub(name: String) = subs.firstOrNull { it.name == name }

        /** Belanja kartu kredit yang belum dilunasi; terbawa antar bulan sampai ada pembayaran. */
        private var cardSpent = 0L

        fun addMonth(month: YearMonth, index: Int) {
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                if (date.isAfter(today)) break

                // ---- Pemasukan ----
                if (day == 25) income("Gaji", date, 8_500_000L + index * 150_000L, bank, "Gaji bulanan", recurringIds["Gaji"])
                if (day == 12 && rnd.nextDouble() < 0.6) {
                    income("Usaha", date, round(rnd.nextLong(800_000, 2_500_000), 50_000), bank, "Proyek sampingan")
                }
                if (day == 20 && month.monthValue == 12) income("Bonus", date, 4_000_000, bank, "Bonus akhir tahun")

                // ---- Pengeluaran rutin bulanan ----
                if (day == 1) expense("Tagihan", "Sewa/KPR", date, 1_800_000, bank, "Sewa kontrakan", recurringIds["Sewa"])
                if (day == 5) expense("Tagihan", "Listrik", date, round(rnd.nextLong(320_000, 480_000), 1_000), bank, "Token listrik")
                if (day == 6) expense("Tagihan", "Air", date, round(rnd.nextLong(70_000, 95_000), 1_000), bank, "PDAM")
                if (day == 7) expense("Tagihan", "Internet", date, 399_000, bank, "Internet rumah")
                if (day == 8) expense("Tagihan", "Pulsa", date, 100_000, ewallet, "Paket data")
                if (day == 10) {
                    expense("Hiburan", "Langganan", date, 54_000, card, "Streaming musik", recurringIds["Musik"]); cardSpent += 54_000
                    expense("Hiburan", "Langganan", date, 186_000, card, "Streaming video"); cardSpent += 186_000
                }
                if (day == 15) expense("Cicilan", null, date, 750_000, bank, "Cicilan perangkat", recurringIds["Cicilan"])

                // ---- Transfer antar dompet ----
                if (day == 2) transfer(date, 2_000_000, bank, cash, "Tarik tunai")
                if (day == 3) transfer(date, 2_200_000, bank, ewallet, "Isi saldo e-wallet")

                // ---- Pengeluaran harian acak ----
                if (rnd.nextDouble() < 0.85) {
                    when (rnd.nextInt(100)) {
                        in 0..59 -> expense("Makanan", "Makan di luar", date, round(rnd.nextLong(18_000, 95_000), 500), pickDaily(), pick(FOOD_NOTES))
                        in 60..84 -> expense("Makanan", "Kopi & jajan", date, round(rnd.nextLong(12_000, 55_000), 500), pickDaily(), pick(COFFEE_NOTES))
                        else -> expense("Makanan", "Bahan makanan", date, round(rnd.nextLong(60_000, 250_000), 1_000), pickDaily(), "Belanja dapur")
                    }
                }
                if (rnd.nextDouble() < 0.45) {
                    when (rnd.nextInt(100)) {
                        in 0..49 -> expense("Transport", "Ojek online", date, round(rnd.nextLong(10_000, 45_000), 500), ewallet, "Ojek online")
                        in 50..74 -> expense("Transport", "Angkutan umum", date, round(rnd.nextLong(7_000, 20_000), 500), cash, "Commuter/bus")
                        else -> expense("Transport", "Bensin", date, round(rnd.nextLong(50_000, 150_000), 5_000), cash, "Isi bensin")
                    }
                }
                if (day % 7 == 0 && rnd.nextDouble() < 0.7) {
                    val amount = round(rnd.nextLong(80_000, 300_000), 1_000)
                    val useCard = rnd.nextBoolean()
                    expense("Belanja", "Kebutuhan rumah", date, amount, if (useCard) card else bank, "Belanja kebutuhan rumah")
                    if (useCard) cardSpent += amount
                }
                if (rnd.nextDouble() < 0.12) {
                    expense("Hiburan", if (rnd.nextBoolean()) "Wisata" else "Game", date, round(rnd.nextLong(40_000, 250_000), 5_000), ewallet, "Hiburan akhir pekan")
                }
                if (rnd.nextDouble() < 0.06) {
                    val amount = round(rnd.nextLong(100_000, 800_000), 10_000)
                    expense("Belanja", if (rnd.nextBoolean()) "Pakaian" else "Elektronik", date, amount, card, "Belanja online")
                    cardSpent += amount
                }
                if (rnd.nextDouble() < 0.02) {
                    expense("Kesehatan", "Dokter & obat", date, round(rnd.nextLong(80_000, 400_000), 5_000), cash, "Obat & periksa")
                }

                // ---- Bayar tagihan kartu kredit ----
                if (day == 20 && cardSpent > 0) {
                    transfer(date, round(cardSpent, 1_000), bank, card, "Bayar tagihan kartu kredit")
                    cardSpent = 0
                }
            }
        }

        private fun pickDaily(): Long = when (rnd.nextInt(100)) {
            in 0..49 -> cash
            in 50..84 -> ewallet
            else -> card
        }

        private fun pick(options: List<String>) = options[rnd.nextInt(options.size)]

        private fun round(value: Long, step: Long): Long = (value / step) * step.coerceAtLeast(1)

        private fun timeOf(date: LocalDate): Long =
            LocalDateTime.of(date, java.time.LocalTime.of(rnd.nextInt(7, 22), rnd.nextInt(0, 60))).toEpochMillis()

        private fun income(parentName: String, date: LocalDate, amount: Long, wallet: Long, note: String, recurringId: Long? = null) {
            val category = parent(parentName, CategoryKind.INCOME) ?: return
            add(TransactionType.INCOME, amount, wallet, null, category.id, null, note, date, recurringId)
        }

        private fun expense(parentName: String, subName: String?, date: LocalDate, amount: Long, wallet: Long, note: String, recurringId: Long? = null) {
            val category = parent(parentName, CategoryKind.EXPENSE) ?: return
            val subCategory = subName?.let { n -> subs.firstOrNull { it.name == n && it.parentId == category.id } }
            add(TransactionType.EXPENSE, amount, wallet, null, category.id, subCategory?.id, note, date, recurringId)
        }

        private fun transfer(date: LocalDate, amount: Long, from: Long, to: Long, note: String) {
            add(TransactionType.TRANSFER, amount, from, to, null, null, note, date)
        }

        private fun add(
            type: TransactionType,
            amount: Long,
            walletId: Long,
            toWalletId: Long?,
            categoryId: Long?,
            subCategoryId: Long?,
            note: String,
            date: LocalDate,
            recurringId: Long? = null,
        ) {
            if (amount <= 0) return
            var at = timeOf(date)
            // Jangan buat transaksi di masa depan (hari ini, jam yang belum lewat).
            if (at > now) at = now
            result += TransactionEntity(
                type = type,
                amount = amount,
                walletId = walletId,
                toWalletId = toWalletId,
                categoryId = categoryId,
                subCategoryId = subCategoryId,
                note = note,
                tags = if (type == TransactionType.EXPENSE && note.startsWith("Belanja")) listOf("belanja") else emptyList(),
                occurredAt = at,
                recurringId = recurringId,
                createdAt = at,
                updatedAt = at,
            )
        }

        companion object {
            val FOOD_NOTES = listOf("Makan siang", "Makan malam", "Sarapan", "Warteg", "Nasi goreng", "Bakso", "Soto ayam")
            val COFFEE_NOTES = listOf("Kopi", "Boba", "Roti & teh", "Camilan")
        }
    }
}

package com.duitku.app.data.backup

import androidx.room.withTransaction
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.DataTransferDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.entity.CategoryEntity
import javax.inject.Inject
import javax.inject.Singleton

data class MergeStats(
    val walletsAdded: Int,
    val transactionsAdded: Int,
    val skippedDuplicate: Int,
    val skippedClosed: Int,
    val statementsAdded: Int,
)

/** Membaca seluruh data ke [BackupPayload] dan menulisnya kembali (timpa / gabung) dalam satu transaksi database. */
@Singleton
class BackupService @Inject constructor(
    private val database: DuitKuDatabase,
    private val dao: DataTransferDao,
    private val statementDao: StatementDao,
) {
    /** Salinan konsisten semua tabel (dibaca dalam satu transaksi). */
    suspend fun collect(settings: BackupSettings): BackupPayload = database.withTransaction {
        BackupPayload(
            wallets = dao.wallets(), categories = dao.categories(), recurring = dao.recurring(), transactions = dao.transactions(),
            budgets = dao.budgets(), goals = dao.goals(), contributions = dao.contributions(), debts = dao.debts(),
            payments = dao.payments(), statements = dao.statements(), walletBalances = dao.walletBalances(),
            auditLogs = dao.auditLogs(), settings = settings,
        )
    }

    /** Mengganti seluruh data dengan isi [payload]. Gagal di tengah jalan = semua perubahan dibatalkan. */
    suspend fun overwrite(payload: BackupPayload) = database.withTransaction {
        clearAll(keepExportAndBackupHistory = true)
        dao.putWallets(payload.wallets)
        dao.putCategories(payload.categories.sortedWith(compareBy({ it.parentId != null }, { it.id })))
        dao.putRecurring(payload.recurring)
        dao.putTransactions(payload.transactions)
        dao.putBudgets(payload.budgets)
        dao.putGoals(payload.goals)
        dao.putContributions(payload.contributions)
        dao.putDebts(payload.debts)
        dao.putPayments(payload.payments)
        dao.putStatements(payload.statements)
        dao.putWalletBalances(payload.walletBalances)
        dao.putAuditLogs(payload.auditLogs)
    }

    /** Menghapus semua data pengguna. Urutan mengikuti foreign key. */
    suspend fun clearAll(keepExportAndBackupHistory: Boolean = false) = database.withTransaction {
        dao.clearTransactions()
        dao.clearBudgets()
        dao.clearRecurring()
        dao.clearContributions()
        dao.clearGoals()
        dao.clearPayments()
        dao.clearDebts()
        dao.clearWalletBalances()
        dao.clearStatements()
        dao.clearAuditLogs()
        if (!keepExportAndBackupHistory) {
            dao.clearExportHistory()
            dao.clearBackupRecords()
        }
        dao.clearCategories()
        dao.clearWallets()
    }

    /**
     * Menambahkan data dari backup yang belum ada, tanpa mengubah data saat ini.
     * Transaksi yang jatuh di bulan yang sudah ditutup dilewati agar arsip tidak berubah diam-diam.
     */
    suspend fun merge(payload: BackupPayload, startDay: Int): MergeStats = database.withTransaction {
        val closed = statementDao.getClosedPeriods().toSet()

        // Dompet: dicocokkan berdasarkan nama.
        val existingWallets = dao.wallets()
        val walletMap = HashMap<Long, Long>()
        var walletsAdded = 0
        for (w in payload.wallets) {
            val match = existingWallets.firstOrNull { it.name.equals(w.name, ignoreCase = true) }
            walletMap[w.id] = match?.id ?: dao.addWallet(w.copy(id = 0)).also { walletsAdded++ }
        }

        // Kategori: dicocokkan berdasarkan (jenis, nama, nama induk). Induk diproses lebih dulu.
        val existingCats = dao.categories()
        val catMap = HashMap<Long, Long>()
        val backupById = payload.categories.associateBy { it.id }
        fun key(c: CategoryEntity, all: Map<Long, CategoryEntity>) =
            Triple(c.kind, c.name.lowercase(), c.parentId?.let { all[it]?.name?.lowercase() })
        val existingKeys = existingCats.associateBy { key(it, existingCats.associateBy { e -> e.id }) }
        for (c in payload.categories.sortedBy { it.parentId != null }) {
            val k = key(c, backupById)
            val match = existingKeys[k]
            catMap[c.id] = match?.id ?: dao.addCategory(c.copy(id = 0, parentId = c.parentId?.let { catMap[it] }))
        }

        // Aturan berulang: berdasarkan judul.
        val existingRecurring = dao.recurring()
        val recurringMap = HashMap<Long, Long>()
        for (r in payload.recurring) {
            val match = existingRecurring.firstOrNull { it.title.equals(r.title, ignoreCase = true) }
            val newWallet = walletMap[r.walletId] ?: continue
            if (match != null) {
                recurringMap[r.id] = match.id
            } else {
                recurringMap[r.id] = recurringInsert(r.copy(id = 0, walletId = newWallet, toWalletId = r.toWalletId?.let { walletMap[it] },
                    categoryId = r.categoryId?.let { catMap[it] }, subCategoryId = r.subCategoryId?.let { catMap[it] }))
            }
        }

        // Transaksi: kunci duplikat = dompet, tipe, nominal, waktu, catatan.
        data class TxKey(val wallet: Long, val to: Long?, val type: String, val amount: Long, val at: Long, val note: String)
        val seen = dao.transactions().map { TxKey(it.walletId, it.toWalletId, it.type.name, it.amount, it.occurredAt, it.note) }.toHashSet()
        var added = 0
        var duplicate = 0
        var skippedClosed = 0
        for (t in payload.transactions) {
            val wallet = walletMap[t.walletId] ?: continue
            val to = t.toWalletId?.let { walletMap[it] }
            val k = TxKey(wallet, to, t.type.name, t.amount, t.occurredAt, t.note)
            if (k in seen) { duplicate++; continue }
            if (PeriodCalculator.periodOfMillis(t.occurredAt, startDay).toKey() in closed) { skippedClosed++; continue }
            dao.addTransaction(
                t.copy(
                    id = 0, walletId = wallet, toWalletId = to, categoryId = t.categoryId?.let { catMap[it] },
                    subCategoryId = t.subCategoryId?.let { catMap[it] }, recurringId = t.recurringId?.let { recurringMap[it] },
                ),
            )
            seen += k
            added++
        }

        // Anggaran: satu per (periode, kategori).
        val existingBudgets = dao.budgets().map { it.periodYm to it.categoryId }.toHashSet()
        for (b in payload.budgets) {
            val cat = b.categoryId?.let { catMap[it] ?: continue }
            if ((b.periodYm to cat) !in existingBudgets) dao.addBudget(b.copy(id = 0, categoryId = cat))
        }

        // Target tabungan: berdasarkan nama (beserta setorannya bila baru).
        val existingGoals = dao.goals()
        for (g in payload.goals) {
            if (existingGoals.any { it.name.equals(g.name, ignoreCase = true) }) continue
            val newId = dao.addGoal(g.copy(id = 0))
            payload.contributions.filter { it.goalId == g.id }.forEach { dao.addContribution(it.copy(id = 0, goalId = newId)) }
        }

        // Utang/piutang: berdasarkan jenis, pihak, pokok, dan waktu dibuat.
        val existingDebts = dao.debts()
        for (d in payload.debts) {
            if (existingDebts.any { it.kind == d.kind && it.partyName.equals(d.partyName, true) && it.principalAmount == d.principalAmount && it.createdAt == d.createdAt }) continue
            val newId = dao.addDebt(d.copy(id = 0))
            payload.payments.filter { it.debtId == d.id }.forEach { dao.addPayment(it.copy(id = 0, debtId = newId)) }
        }

        // Arsip bulanan: dilewati bila nomor dokumen atau (periode, revisi) sudah ada.
        val existingStatements = dao.statements()
        var statementsAdded = 0
        for (s in payload.statements) {
            val clash = existingStatements.any { it.documentNumber == s.documentNumber || (it.periodYm == s.periodYm && it.revision == s.revision) }
            if (clash) continue
            val newId = dao.addStatement(s.copy(id = 0))
            payload.walletBalances.filter { it.statementId == s.id }.forEach { dao.addWalletBalance(it.copy(id = 0, statementId = newId)) }
            statementsAdded++
        }

        MergeStats(walletsAdded, added, duplicate, skippedClosed, statementsAdded)
    }

    private suspend fun recurringInsert(entity: com.duitku.app.data.local.entity.RecurringTransactionEntity): Long =
        database.recurringDao().insert(entity)

    /** Pembersihan retensi: pindahkan efek transaksi lama ke saldo awal dompet lalu hapus transaksi tersebut. */
    suspend fun purgeBefore(cutoffMillis: Long): Int = database.withTransaction {
        val balances = database.transactionDao().getBalancesBefore(cutoffMillis).associate { it.walletId to it.balance }
        for (w in dao.wallets()) {
            val counted = if (w.openedAt < cutoffMillis) w.initialBalance else 0L
            val flowBefore = (balances[w.id] ?: 0L) - counted
            val newInitial = w.initialBalance + flowBefore
            // Saldo awal baru berlaku tepat sebelum batas, jadi periode setelah batas memakainya sebagai saldo pembuka.
            val newOpenedAt = if (w.openedAt < cutoffMillis) cutoffMillis - 1 else w.openedAt
            if (flowBefore != 0L || newOpenedAt != w.openedAt) dao.carryForward(w.id, newInitial, newOpenedAt)
        }
        database.transactionDao().deleteBefore(cutoffMillis)
    }
}

package com.duitku.app.data.repository

import androidx.room.withTransaction
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.data.local.Converters
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.AuditLogDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.mapper.toDomain
import com.duitku.app.data.mapper.toEntity
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.repository.PeriodClosedException
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val database: DuitKuDatabase,
    private val transactionDao: TransactionDao,
    private val statementDao: StatementDao,
    private val auditLogDao: AuditLogDao,
    private val settingsRepository: SettingsRepository,
) : TransactionRepository {

    override fun observeFiltered(filter: TransactionFilter): Flow<List<Transaction>> =
        transactionDao.observeFiltered(TransactionFilterQuery.toSupportQuery(filter))
            .map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Transaction? = transactionDao.getById(id)?.toDomain()

    override suspend fun save(transaction: Transaction): Long = database.withTransaction {
        val startDay = settingsRepository.current().monthStartDay
        val closed = statementDao.getClosedPeriods().toSet()
        val now = System.currentTimeMillis()

        val old = if (transaction.id != 0L) transactionDao.getById(transaction.id) else null
        val newPeriod = PeriodCalculator.periodOfMillis(transaction.occurredAt, startDay).toKey()
        val oldPeriod = old?.let { PeriodCalculator.periodOfMillis(it.occurredAt, startDay).toKey() }

        if (newPeriod in closed) throw PeriodClosedException(newPeriod)
        if (oldPeriod != null && oldPeriod in closed) throw PeriodClosedException(oldPeriod)

        val id: Long
        if (old == null) {
            id = transactionDao.insert(transaction.copy(id = 0, createdAt = now, updatedAt = now).toEntity())
            logIfArchived(newPeriod, id, "CREATE", "Transaksi ditambahkan", null, describe(transaction))
        } else {
            val updated = transaction.copy(createdAt = old.createdAt, updatedAt = now)
            transactionDao.update(updated.toEntity())
            id = old.id
            logIfArchived(newPeriod, id, "UPDATE", "Transaksi diubah", describe(old), describe(updated.toEntity()))
            if (oldPeriod != null && oldPeriod != newPeriod) {
                logIfArchived(oldPeriod, id, "UPDATE", "Transaksi dipindahkan ke periode lain", describe(old), describe(updated.toEntity()))
            }
        }
        id
    }

    override suspend fun delete(id: Long): Transaction? = database.withTransaction {
        val existing = transactionDao.getById(id) ?: return@withTransaction null
        val startDay = settingsRepository.current().monthStartDay
        val period = PeriodCalculator.periodOfMillis(existing.occurredAt, startDay).toKey()
        if (period in statementDao.getClosedPeriods().toSet()) throw PeriodClosedException(period)

        transactionDao.deleteById(id)
        logIfArchived(period, id, "DELETE", "Transaksi dihapus", describe(existing), null)
        existing.toDomain()
    }

    override suspend fun restore(transaction: Transaction) {
        database.withTransaction {
            val startDay = settingsRepository.current().monthStartDay
            val period = PeriodCalculator.periodOfMillis(transaction.occurredAt, startDay).toKey()
            if (period in statementDao.getClosedPeriods().toSet()) throw PeriodClosedException(period)
            transactionDao.insertWithId(transaction.toEntity())
            logIfArchived(period, transaction.id, "CREATE", "Penghapusan transaksi diurungkan", null, describe(transaction))
        }
    }

    override fun observeOldestAt(): Flow<Long?> = transactionDao.observeOldestAt()

    override fun observeCount(): Flow<Int> = transactionDao.observeCount()

    override suspend fun allTags(): List<String> {
        val converter = Converters()
        return transactionDao.getDistinctTagStrings()
            .flatMap { converter.stringToTags(it) }
            .distinct()
            .sorted()
    }

    /** Mencatat audit hanya bila periode sudah punya arsip (pernah ditutup lalu dibuka kembali). */
    private suspend fun logIfArchived(
        periodYm: Int,
        entityId: Long,
        action: String,
        description: String,
        oldValue: String?,
        newValue: String?,
    ) {
        if (statementDao.getActive(periodYm) == null) return
        auditLogDao.insert(
            AuditLogEntity(
                createdAt = System.currentTimeMillis(),
                periodYm = periodYm,
                entityType = "TRANSACTION",
                entityId = entityId,
                action = action,
                description = description,
                oldValue = oldValue,
                newValue = newValue,
            ),
        )
    }

    private fun describe(t: Transaction): String = describe(t.toEntity())

    private fun describe(e: TransactionEntity): String = buildString {
        append(e.type.label).append(' ').append(MoneyFormatter.format(e.amount))
        append(" | ").append(DateFormats.dateTime(e.occurredAt))
        append(" | dompet#").append(e.walletId)
        e.toWalletId?.let { append("->#").append(it) }
        e.categoryId?.let { append(" | kategori#").append(it) }
        if (e.note.isNotBlank()) append(" | ").append(e.note)
        if (e.tags.isNotEmpty()) append(" | ").append(e.tags.joinToString(" ") { "#$it" })
    }
}

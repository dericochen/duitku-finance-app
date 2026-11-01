package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.data.local.entity.BackupRecordEntity
import com.duitku.app.data.local.entity.ExportHistoryEntity
import com.duitku.app.data.local.entity.MonthlyStatementEntity
import com.duitku.app.data.local.entity.MonthlyWalletBalanceEntity
import com.duitku.app.domain.model.StatementStatus
import kotlinx.coroutines.flow.Flow

/** Proyeksi ringan tanpa kolom snapshotJson agar daftar arsip tidak memuat data besar. */
data class StatementSummaryRow(
    val id: Long,
    val periodYm: Int,
    val startDate: Long,
    val endDate: Long,
    val openingBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val totalTransfer: Long,
    val closingBalance: Long,
    val netChange: Long,
    val transactionCount: Int,
    val status: StatementStatus,
    val closedAt: Long?,
    val revision: Int,
    val documentNumber: String,
    val checksum: String,
    val userNote: String,
    val createdAt: Long,
)

private const val SUMMARY_COLUMNS =
    "id, periodYm, startDate, endDate, openingBalance, totalIncome, totalExpense, totalTransfer, " +
        "closingBalance, netChange, transactionCount, status, closedAt, revision, documentNumber, " +
        "checksum, userNote, createdAt"

@Dao
interface StatementDao {
    @Insert
    suspend fun insert(entity: MonthlyStatementEntity): Long

    @Update
    suspend fun update(entity: MonthlyStatementEntity)

    @Insert
    suspend fun insertWalletBalances(entities: List<MonthlyWalletBalanceEntity>)

    /** Revisi terbaru tiap periode (status SUPERSEDED tidak ditampilkan di daftar). */
    @Query(
        """SELECT $SUMMARY_COLUMNS FROM monthly_statements
           WHERE status != 'SUPERSEDED'
           ORDER BY periodYm DESC, revision DESC""",
    )
    fun observeLatestSummaries(): Flow<List<StatementSummaryRow>>

    @Query(
        """SELECT $SUMMARY_COLUMNS FROM monthly_statements
           WHERE status != 'SUPERSEDED'
           ORDER BY periodYm DESC, revision DESC""",
    )
    suspend fun getLatestSummaries(): List<StatementSummaryRow>

    @Query("SELECT $SUMMARY_COLUMNS FROM monthly_statements WHERE periodYm = :periodYm ORDER BY revision DESC")
    fun observeRevisions(periodYm: Int): Flow<List<StatementSummaryRow>>

    /** Revisi aktif (bukan SUPERSEDED) untuk satu periode. */
    @Query(
        """SELECT * FROM monthly_statements
           WHERE periodYm = :periodYm AND status != 'SUPERSEDED'
           ORDER BY revision DESC LIMIT 1""",
    )
    suspend fun getActive(periodYm: Int): MonthlyStatementEntity?

    @Query(
        """SELECT $SUMMARY_COLUMNS FROM monthly_statements
           WHERE periodYm = :periodYm AND status != 'SUPERSEDED'
           ORDER BY revision DESC LIMIT 1""",
    )
    fun observeActiveSummary(periodYm: Int): Flow<StatementSummaryRow?>

    @Query("SELECT * FROM monthly_statements WHERE id = :id")
    suspend fun getById(id: Long): MonthlyStatementEntity?

    @Query("SELECT COALESCE(MAX(revision), 0) FROM monthly_statements WHERE periodYm = :periodYm")
    suspend fun maxRevision(periodYm: Int): Int

    @Query("SELECT COUNT(*) FROM monthly_statements WHERE documentNumber = :documentNumber")
    suspend fun countDocumentNumber(documentNumber: String): Int

    @Query("UPDATE monthly_statements SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: StatementStatus)

    @Query("UPDATE monthly_statements SET userNote = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("SELECT * FROM monthly_wallet_balances WHERE statementId = :statementId ORDER BY walletId")
    suspend fun getWalletBalances(statementId: Long): List<MonthlyWalletBalanceEntity>

    /** Periode yang sedang tertutup (revisi aktif berstatus CLOSED). */
    @Query(
        """SELECT periodYm FROM monthly_statements
           WHERE status = 'CLOSED' ORDER BY periodYm DESC""",
    )
    fun observeClosedPeriods(): Flow<List<Int>>

    @Query("SELECT periodYm FROM monthly_statements WHERE status = 'CLOSED'")
    suspend fun getClosedPeriods(): List<Int>

    @Query("SELECT COUNT(*) FROM monthly_statements WHERE status = 'CLOSED'")
    fun observeClosedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(LENGTH(snapshotJson)), 0) FROM monthly_statements")
    suspend fun snapshotBytes(): Long

    @Query("DELETE FROM monthly_statements")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entity: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE periodYm = :periodYm ORDER BY createdAt DESC, id DESC")
    fun observeForPeriod(periodYm: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<AuditLogEntity>

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

@Dao
interface ExportHistoryDao {
    @Insert
    suspend fun insert(entity: ExportHistoryEntity): Long

    @Query("SELECT * FROM export_history ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ExportHistoryEntity>>

    @Query("SELECT * FROM export_history ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ExportHistoryEntity>

    @Query("SELECT * FROM export_history WHERE id = :id")
    suspend fun getById(id: Long): ExportHistoryEntity?

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM export_history")
    suspend fun deleteAll()
}

@Dao
interface BackupRecordDao {
    @Insert
    suspend fun insert(entity: BackupRecordEntity): Long

    @Query("SELECT * FROM backup_records ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<BackupRecordEntity>

    @Query("SELECT * FROM backup_records WHERE success = 1 ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLastSuccess(): Flow<BackupRecordEntity?>

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM backup_records")
    suspend fun deleteAll()
}

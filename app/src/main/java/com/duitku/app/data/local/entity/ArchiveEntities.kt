package com.duitku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.StatementStatus

/**
 * Arsip mutasi satu periode (tutup buku). Satu baris per revisi.
 * Isi [snapshotJson] + [checksum] tidak pernah diubah setelah dibuat; revisi baru = baris baru.
 *
 * Catatan ukuran: SQLite pada Android membaca satu baris maksimal ~2 MB (CursorWindow).
 * Snapshot memakai kunci JSON pendek; layar daftar memakai proyeksi tanpa kolom ini.
 */
@Serializable
@Entity(
    tableName = "monthly_statements",
    indices = [
        Index(value = ["periodYm", "revision"], unique = true),
        Index(value = ["documentNumber"], unique = true),
        Index("status"),
    ],
)
data class MonthlyStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val closedAt: Long? = null,
    val revision: Int = 1,
    /** Contoh: DKU-202611-0001. */
    val documentNumber: String,
    val snapshotJson: String,
    /** SHA-256 (hex) dari [snapshotJson]. */
    val checksum: String,
    /** Catatan bulan dari pengguna, ikut dicetak di halaman penutup PDF. */
    val userNote: String = "",
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "monthly_wallet_balances",
    foreignKeys = [
        ForeignKey(MonthlyStatementEntity::class, ["id"], ["statementId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("statementId"), Index("periodYm"), Index("walletId")],
)
data class MonthlyWalletBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val statementId: Long,
    val periodYm: Int,
    /** Tanpa FK: dompet boleh dihapus kelak, arsip tetap utuh. */
    val walletId: Long,
    val walletName: String,
    val openingBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val transferIn: Long,
    val transferOut: Long,
    val closingBalance: Long,
)

@Serializable
@Entity(
    tableName = "audit_logs",
    indices = [Index("periodYm"), Index("createdAt")],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val periodYm: Int? = null,
    /** TRANSACTION, STATEMENT, BACKUP, RETENTION, dst. */
    val entityType: String,
    val entityId: Long? = null,
    /** CREATE, UPDATE, DELETE, REOPEN, CLOSE, RESTORE, PURGE, dst. */
    val action: String,
    val description: String,
    val oldValue: String? = null,
    val newValue: String? = null,
)

@Serializable
@Entity(
    tableName = "export_history",
    indices = [Index("createdAt")],
)
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    /** Path relatif di filesDir/exports atau content URI hasil SAF. */
    val location: String,
    val format: ExportFormat,
    val kind: ExportKind,
    val fromPeriodYm: Int,
    val toPeriodYm: Int,
    val documentNumber: String? = null,
    val checksum: String? = null,
    val sizeBytes: Long,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "backup_records",
    indices = [Index("createdAt")],
)
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val location: String,
    val kind: BackupKind,
    val dbVersion: Int,
    val transactionCount: Int,
    val sizeBytes: Long,
    val checksum: String,
    val success: Boolean = true,
    val message: String = "",
    val createdAt: Long,
)

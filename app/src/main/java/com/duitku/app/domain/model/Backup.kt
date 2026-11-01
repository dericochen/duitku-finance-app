package com.duitku.app.domain.model

enum class RestoreMode(val label: String) {
    /** Hapus data saat ini, ganti seluruhnya dengan isi backup. */
    OVERWRITE("Timpa (ganti semua data)"),

    /** Tambahkan hanya data yang belum ada; data saat ini dipertahankan. */
    MERGE("Gabung (tambahkan yang belum ada)"),
}

data class BackupInfo(
    val createdAt: Long,
    val dbVersion: Int,
    val appVersion: String,
    val wallets: Int,
    val transactions: Int,
    val statements: Int,
    val oldestTransactionAt: Long?,
    val newestTransactionAt: Long?,
    val sizeBytes: Long,
    val checksum: String,
)

sealed interface InspectResult {
    data class Valid(val info: BackupInfo) : InspectResult
    data class Invalid(val reason: String) : InspectResult
}

data class RestoreReport(
    val mode: RestoreMode,
    val walletsAdded: Int,
    val transactionsAdded: Int,
    val transactionsSkippedDuplicate: Int,
    val transactionsSkippedClosed: Int,
    val statementsAdded: Int,
    val safetyBackupName: String?,
)

sealed interface RestoreResult {
    data class Success(val report: RestoreReport) : RestoreResult
    data class Failed(val reason: String) : RestoreResult
}

data class BackupOutcome(
    val fileName: String,
    val location: String,
    val sizeBytes: Long,
    val checksum: String,
    val transactionCount: Int,
)

data class BackupRecordInfo(
    val id: Long,
    val fileName: String,
    val kind: BackupKind,
    val sizeBytes: Long,
    val createdAt: Long,
    val success: Boolean,
    val message: String,
    val transactionCount: Int,
)

data class StorageUsage(
    val databaseBytes: Long,
    val exportBytes: Long,
    val backupBytes: Long,
    val transactionCount: Int,
    val statementCount: Int,
    val snapshotBytes: Long,
) {
    val totalBytes: Long get() = databaseBytes + exportBytes + backupBytes
}

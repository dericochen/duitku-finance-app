package com.duitku.app.domain.repository

import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.RetentionAction

data class RetentionStatus(
    val retentionMonths: Int,
    val action: RetentionAction,
    val cutoffMillis: Long?,
    val oldestTransactionAt: Long?,
    /** Jumlah transaksi mentah yang sudah melewati masa retensi. */
    val expiredTransactionCount: Int,
    val expiredPeriods: List<Int>,
    /** Transaksi yang akan kedaluwarsa dalam 30 hari ke depan. */
    val expiresSoon: Boolean,
    /** Arsip + backup untuk [cutoffMillis] sudah disiapkan sehingga pembersihan boleh dikonfirmasi. */
    val prepared: Boolean,
    val preparedAt: Long,
) {
    val hasExpiredData: Boolean get() = expiredTransactionCount > 0
    val canPurge: Boolean get() = prepared && hasExpiredData && action == RetentionAction.ASK_BEFORE_PURGE
    val retentionLabel: String get() = AppSettings.retentionLabel(retentionMonths)
}

sealed interface PrepareResult {
    data object NothingToDo : PrepareResult
    data class Ready(val closedPeriods: Int, val reportsCreated: Int, val backupName: String, val transactionCount: Int) : PrepareResult
    data class Failed(val reason: String) : PrepareResult
}

sealed interface PurgeResult {
    data class Done(val deleted: Int) : PurgeResult
    data class Refused(val reason: String) : PurgeResult
}

interface RetentionRepository {
    suspend fun status(): RetentionStatus

    /** Menutup bulan kedaluwarsa, membuat backup + PDF laporan tahunan. Tidak menghapus apa pun. */
    suspend fun prepare(onProgress: (Int, String) -> Unit = { _, _ -> }): PrepareResult

    /** Menghapus transaksi mentah yang kedaluwarsa. Hanya bila arsip sudah disiapkan dan pengguna mengonfirmasi. */
    suspend fun purge(): PurgeResult
}

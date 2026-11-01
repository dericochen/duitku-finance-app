package com.duitku.app.domain.repository

import com.duitku.app.domain.model.AuditEntry
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.LiveMonth
import com.duitku.app.domain.model.ReopenResult
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.model.StatementSummary
import com.duitku.app.domain.model.VerifyResult
import kotlinx.coroutines.flow.Flow

interface StatementRepository {
    /** Mutasi satu periode secara langsung (selalu mengikuti data terbaru). [walletId] null = semua dompet. */
    fun observeMonth(periodYm: Int, walletId: Long?): Flow<LiveMonth>

    fun observeSummaries(): Flow<List<StatementSummary>>
    fun observeActive(periodYm: Int): Flow<StatementSummary?>
    suspend fun getActive(periodYm: Int): StatementSummary?
    suspend fun getSummaryById(id: Long): StatementSummary?
    fun observeClosedCount(): Flow<Int>

    /** Tutup buku: membuat snapshot permanen + checksum. */
    suspend fun close(periodYm: Int): CloseResult

    /** Membuka kembali bulan tertutup. Dicatat di audit log. */
    suspend fun reopen(periodYm: Int, reason: String): ReopenResult

    suspend fun verify(statementId: Long): VerifyResult
    suspend fun getSnapshot(statementId: Long): StatementSnapshot?

    /** Snapshot dari data saat ini (untuk bulan yang belum ditutup / PDF Draft). */
    suspend fun buildLiveSnapshot(periodYm: Int): StatementSnapshot

    suspend fun updateNote(statementId: Long, note: String)

    /** Periode (YYYYMM) yang sudah berakhir lebih dari [graceDays] hari, punya transaksi, dan belum diarsipkan. */
    suspend fun periodsDueForAutoClose(graceDays: Int, today: java.time.LocalDate = java.time.LocalDate.now()): List<Int>

    fun observeAudit(limit: Int): Flow<List<AuditEntry>>
    fun observeAuditForPeriod(periodYm: Int): Flow<List<AuditEntry>>
}

interface BudgetRepository {
    fun observeLines(periodYm: Int): Flow<List<BudgetLine>>
    suspend fun getLines(periodYm: Int): List<BudgetLine>
    suspend fun get(periodYm: Int, categoryId: Long?): Budget?
    suspend fun save(budget: Budget): Long
    suspend fun delete(id: Long)

    /** Menyalin anggaran periode sebelumnya yang belum ada di periode ini. Mengembalikan jumlah yang disalin. */
    suspend fun copyFromPrevious(periodYm: Int): Int
}

/** Memeriksa pemakaian anggaran setelah pengeluaran dicatat dan memberi notifikasi pada 80% dan 100%. */
interface BudgetAlerts {
    /** @param occurredAt waktu transaksi yang baru dicatat; periode anggarannya yang diperiksa. */
    suspend fun check(occurredAt: Long)
}

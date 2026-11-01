package com.duitku.app.domain.repository

import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.BackupOutcome
import com.duitku.app.domain.model.BackupRecordInfo
import com.duitku.app.domain.model.InspectResult
import com.duitku.app.domain.model.RestoreMode
import com.duitku.app.domain.model.RestoreResult
import com.duitku.app.domain.model.StorageUsage
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    /** Membuat backup dan menulisnya ke folder pilihan (SAF) atau penyimpanan internal bila belum diatur. */
    suspend fun backupToConfiguredLocation(kind: BackupKind): BackupOutcome

    /** Menulis backup ke URI dokumen hasil Storage Access Framework (CreateDocument). */
    suspend fun backupToUri(uri: String, kind: BackupKind = BackupKind.MANUAL): BackupOutcome

    suspend fun inspect(uri: String): InspectResult
    suspend fun restore(uri: String, mode: RestoreMode): RestoreResult

    fun observeRecords(): Flow<List<BackupRecordInfo>>
    suspend fun storageUsage(): StorageUsage
}

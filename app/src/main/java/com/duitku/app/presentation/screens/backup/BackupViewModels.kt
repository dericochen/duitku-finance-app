package com.duitku.app.presentation.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.BackupFrequency
import com.duitku.app.domain.model.BackupInfo
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.BackupRecordInfo
import com.duitku.app.domain.model.InspectResult
import com.duitku.app.domain.model.RestoreMode
import com.duitku.app.domain.model.RestoreResult
import com.duitku.app.domain.model.RetentionAction
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.PrepareResult
import com.duitku.app.domain.repository.PurgeResult
import com.duitku.app.domain.repository.RetentionRepository
import com.duitku.app.domain.repository.RetentionStatus
import com.duitku.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingRestore(val uri: String, val info: BackupInfo)

data class BackupUiState(
    val settings: AppSettings = AppSettings(),
    val records: List<BackupRecordInfo> = emptyList(),
    val busy: Boolean = false,
    val pending: PendingRestore? = null,
    val resultMessage: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val pending = MutableStateFlow<PendingRestore?>(null)
    private val result = MutableStateFlow<String?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private data class Head(val settings: AppSettings, val records: List<BackupRecordInfo>)

    private val head = combine(settingsRepository.settings, backups.observeRecords()) { s, r -> Head(s, r) }

    val uiState: StateFlow<BackupUiState> = combine(head, busy, pending, result) { h, b, p, r ->
        BackupUiState(h.settings, h.records, b, p, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun setFrequency(f: BackupFrequency) {
        viewModelScope.launch { settingsRepository.update { it.copy(backupFrequency = f) } }
    }

    fun setFolder(uri: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(backupFolderUri = uri) }
            _messages.send("Folder backup disimpan")
        }
    }

    fun clearFolder() {
        viewModelScope.launch { settingsRepository.update { it.copy(backupFolderUri = "") } }
    }

    fun backupNowToFolder() = run("Membuat backup...") {
        val outcome = backups.backupToConfiguredLocation(BackupKind.MANUAL)
        _messages.send("Backup dibuat: ${outcome.fileName}")
    }

    fun backupToUri(uri: String) = run("Membuat backup...") {
        val outcome = backups.backupToUri(uri)
        _messages.send("Backup disimpan (${outcome.transactionCount} transaksi)")
    }

    fun inspect(uri: String) = run("Memeriksa berkas...") {
        when (val r = backups.inspect(uri)) {
            is InspectResult.Valid -> pending.value = PendingRestore(uri, r.info)
            is InspectResult.Invalid -> result.value = "Berkas ditolak: ${r.reason}"
        }
    }

    fun cancelRestore() {
        pending.value = null
    }

    fun dismissResult() {
        result.value = null
    }

    fun restore(mode: RestoreMode) {
        val p = pending.value ?: return
        pending.value = null
        run("Memulihkan data...") {
            result.value = when (val r = backups.restore(p.uri, mode)) {
                is RestoreResult.Success -> {
                    val rp = r.report
                    if (mode == RestoreMode.OVERWRITE) {
                        "Pemulihan selesai: ${rp.walletsAdded} dompet, ${rp.transactionsAdded} transaksi, ${rp.statementsAdded} arsip. " +
                            "Kondisi sebelumnya diamankan di ${rp.safetyBackupName}."
                    } else {
                        "Penggabungan selesai: ${rp.transactionsAdded} transaksi baru, ${rp.transactionsSkippedDuplicate} duplikat dilewati" +
                            (if (rp.transactionsSkippedClosed > 0) ", ${rp.transactionsSkippedClosed} dilewati karena bulannya sudah ditutup" else "") +
                            ", ${rp.statementsAdded} arsip baru. Kondisi sebelumnya diamankan di ${rp.safetyBackupName}."
                    }
                }
                is RestoreResult.Failed -> "Pemulihan gagal: ${r.reason}"
            }
        }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _messages.send("$label gagal: ${e.message}")
            } finally {
                busy.value = false
            }
        }
    }
}

data class RetentionUiState(
    val isLoading: Boolean = true,
    val status: RetentionStatus? = null,
    val retentionMonths: Int = 12,
    val action: RetentionAction = RetentionAction.ASK_BEFORE_PURGE,
    val progress: Pair<Int, String>? = null,
    val message: String? = null,
)

@HiltViewModel
class RetentionViewModel @Inject constructor(
    private val retention: RetentionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RetentionUiState())
    val state: StateFlow<RetentionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                val status = retention.status()
                _state.update { it.copy(isLoading = false, status = status, retentionMonths = s.retentionMonths, action = s.retentionAction) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch { _state.update { it.copy(status = retention.status()) } }
    }

    fun setMonths(months: Int) {
        viewModelScope.launch { settingsRepository.update { it.copy(retentionMonths = months, purgePreparedCutoff = 0L, purgePreparedAt = 0L) } }
    }

    fun setAction(action: RetentionAction) {
        viewModelScope.launch { settingsRepository.update { it.copy(retentionAction = action) } }
    }

    fun prepare() {
        if (_state.value.progress != null) return
        _state.update { it.copy(progress = 0 to "Memulai", message = null) }
        viewModelScope.launch {
            val result = retention.prepare { p, label -> _state.update { it.copy(progress = p to label) } }
            val message = when (result) {
                PrepareResult.NothingToDo -> "Tidak ada data yang melewati masa retensi."
                is PrepareResult.Ready -> "Arsip siap: ${result.closedPeriods} bulan ditutup, ${result.reportsCreated} laporan tahunan PDF dibuat, backup ${result.backupName}."
                is PrepareResult.Failed -> "Gagal menyiapkan arsip: ${result.reason}"
            }
            _state.update { it.copy(progress = null, message = message, status = null) }
            refresh()
        }
    }

    fun purge() {
        viewModelScope.launch {
            val message = when (val r = retention.purge()) {
                is PurgeResult.Done -> "${r.deleted} transaksi mentah dibersihkan. Snapshot bulanan dan PDF tetap tersimpan; saldo dompet tidak berubah."
                is PurgeResult.Refused -> "Tidak dibersihkan: ${r.reason}"
            }
            _state.update { it.copy(message = message) }
            refresh()
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}

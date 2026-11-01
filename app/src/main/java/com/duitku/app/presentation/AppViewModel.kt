package com.duitku.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.data.local.DatabaseIntegrityChecker
import com.duitku.app.di.IoDispatcher
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.presentation.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel tingkat aplikasi: pengaturan global dan pemeriksaan integritas database saat dibuka. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val integrityChecker: DatabaseIntegrityChecker,
    val lock: AppLockManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /** null = pengaturan belum selesai dimuat (tampilkan layar kosong, bukan onboarding palsu). */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _integrityProblem = MutableStateFlow<String?>(null)
    val integrityProblem: StateFlow<String?> = _integrityProblem.asStateFlow()

    init {
        runIntegrityCheck()
    }

    private fun runIntegrityCheck() {
        viewModelScope.launch {
            val report = runCatching { withContext(io) { integrityChecker.check() } }.getOrNull()
            settingsRepository.update {
                it.copy(
                    lastIntegrityCheckAt = System.currentTimeMillis(),
                    lastIntegrityOk = report?.isOk ?: false,
                )
            }
            if (report == null || !report.isOk) {
                _integrityProblem.value = report?.summary ?: "Pemeriksaan database gagal dijalankan"
            }
        }
    }

    fun dismissIntegrityProblem() {
        _integrityProblem.value = null
    }
}

package com.duitku.app.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.data.backup.BackupService
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.StorageUsage
import com.duitku.app.domain.model.ThemeMode
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.DemoDataLoader
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.security.PinHasher
import com.duitku.app.presentation.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val usage: StorageUsage? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val lock: AppLockManager,
    private val backups: BackupRepository,
    private val backupService: BackupService,
    private val demoLoader: DemoDataLoader,
) : ViewModel() {

    private val usage = MutableStateFlow<StorageUsage?>(null)
    private val busy = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, usage, busy) { s, u, b -> SettingsUiState(s, u, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch { usage.value = runCatching { backups.storageUsage() }.getOrNull() }
    }

    private fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(block) }
    }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(v: Boolean) = update { it.copy(dynamicColor = v) }
    fun setCurrency(code: String) = update { it.copy(currencyCode = code) }
    fun setOwner(name: String) = update { it.copy(ownerName = name.trim().take(40)) }
    fun setStartDay(day: Int) = update { it.copy(monthStartDay = day.coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY)) }
    fun setBudgetNotifications(v: Boolean) = update { it.copy(budgetNotificationsEnabled = v) }
    fun setAutoClose(v: Boolean) = update { it.copy(autoCloseMonthEnabled = v) }
    fun setReminder(enabled: Boolean) = update { it.copy(dailyReminderEnabled = enabled) }
    fun setReminderTime(hour: Int, minute: Int) = update { it.copy(reminderHour = hour, reminderMinute = minute, dailyReminderEnabled = true) }
    fun setBiometric(v: Boolean) = update { it.copy(biometricEnabled = v && it.pinEnabled) }

    // ----- PIN -----

    /** @return pesan kesalahan, atau null bila berhasil. */
    suspend fun savePin(current: String, newPin: String, confirm: String): String? {
        val s = settingsRepository.current()
        if (s.pinEnabled && !lock.verifyOnly(current)) return "PIN saat ini salah"
        if (!PinHasher.isValidFormat(newPin)) return "PIN harus ${PinHasher.MIN_LENGTH}-${PinHasher.MAX_LENGTH} digit angka"
        if (newPin != confirm) return "Konfirmasi PIN tidak sama"
        if (PinHasher.isWeak(newPin)) return "PIN terlalu mudah ditebak (mis. 1234 atau 0000)"
        val hashed = PinHasher.hash(newPin)
        settingsRepository.update { it.copy(pinEnabled = true, pinHash = hashed.hash, pinSalt = hashed.salt) }
        lock.markUnlocked()
        _messages.send(if (s.pinEnabled) "PIN diganti" else "PIN diaktifkan")
        return null
    }

    suspend fun removePin(current: String): String? {
        if (!lock.verifyOnly(current)) return "PIN salah"
        settingsRepository.update { it.copy(pinEnabled = false, pinHash = "", pinSalt = "", biometricEnabled = false) }
        _messages.send("PIN dinonaktifkan")
        return null
    }

    // ----- data -----

    fun loadDemoData() {
        viewModelScope.launch {
            if (settingsRepository.current().demoDataLoaded) {
                _messages.send("Data contoh sudah pernah dimuat")
                return@launch
            }
            busy.value = true
            try {
                val count = demoLoader.load()
                _messages.send("$count transaksi contoh ditambahkan")
            } catch (e: Exception) {
                _messages.send("Gagal memuat data contoh: ${e.message}")
            } finally {
                busy.value = false
                refreshUsage()
            }
        }
    }

    /**
     * Reset berlapis: UI sudah meminta persetujuan dua kali; di sini dicek ulang kata kunci dan PIN,
     * lalu dibuat backup pengaman sebelum apa pun dihapus.
     * @return pesan kesalahan, atau null bila berhasil.
     */
    suspend fun resetAll(typedWord: String, pin: String): String? {
        if (typedWord.trim() != RESET_WORD) return "Ketik $RESET_WORD untuk melanjutkan"
        val s = settingsRepository.current()
        if (s.pinEnabled && !lock.verifyOnly(pin)) return "PIN salah"
        busy.value = true
        try {
            // Jika backup pengaman gagal, reset dibatalkan: pengguna tidak boleh kehilangan data tanpa cadangan.
            backups.backupToConfiguredLocation(BackupKind.PRE_RESET)
            backupService.clearAll(keepExportAndBackupHistory = false)
            File(context.filesDir, ExportRepositoryImpl.DIR).listFiles()?.forEach { it.delete() }
            context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("debt_reminders", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("retention_notices", Context.MODE_PRIVATE).edit().clear().apply()
            settingsRepository.update { AppSettings() }
            _messages.send("Semua data dihapus. Backup pengaman tersimpan di penyimpanan aplikasi.")
            return null
        } catch (e: Exception) {
            return "Reset dibatalkan: ${e.message}"
        } finally {
            busy.value = false
        }
    }

    companion object {
        const val RESET_WORD = "HAPUS"
    }
}

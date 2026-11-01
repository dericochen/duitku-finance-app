package com.duitku.app.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            // File preferensi rusak/tidak terbaca: pakai default alih-alih menutup aplikasi.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val ownerName = stringPreferencesKey("owner_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val currencyCode = stringPreferencesKey("currency_code")
        val monthStartDay = intPreferencesKey("month_start_day")
        val retentionMonths = intPreferencesKey("retention_months")
        val retentionAction = stringPreferencesKey("retention_action")
        val pinEnabled = booleanPreferencesKey("pin_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        val dailyReminderEnabled = booleanPreferencesKey("daily_reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val budgetNotifications = booleanPreferencesKey("budget_notifications")
        val autoCloseMonth = booleanPreferencesKey("auto_close_month")
        val backupFrequency = stringPreferencesKey("backup_frequency")
        val backupFolderUri = stringPreferencesKey("backup_folder_uri")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val keepBackupCount = intPreferencesKey("keep_backup_count")
        val purgePreparedCutoff = longPreferencesKey("purge_prepared_cutoff")
        val purgePreparedAt = longPreferencesKey("purge_prepared_at")
        val demoDataLoaded = booleanPreferencesKey("demo_data_loaded")
        val lastIntegrityCheckAt = longPreferencesKey("last_integrity_check_at")
        val lastIntegrityOk = booleanPreferencesKey("last_integrity_ok")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = this[Keys.onboardingDone] ?: d.onboardingDone,
            ownerName = this[Keys.ownerName] ?: d.ownerName,
            themeMode = enumOrDefault(this[Keys.themeMode], d.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            currencyCode = this[Keys.currencyCode] ?: d.currencyCode,
            monthStartDay = (this[Keys.monthStartDay] ?: d.monthStartDay)
                .coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY),
            retentionMonths = this[Keys.retentionMonths] ?: d.retentionMonths,
            retentionAction = enumOrDefault(this[Keys.retentionAction], d.retentionAction),
            pinEnabled = this[Keys.pinEnabled] ?: d.pinEnabled,
            pinHash = this[Keys.pinHash] ?: d.pinHash,
            pinSalt = this[Keys.pinSalt] ?: d.pinSalt,
            biometricEnabled = this[Keys.biometricEnabled] ?: d.biometricEnabled,
            dailyReminderEnabled = this[Keys.dailyReminderEnabled] ?: d.dailyReminderEnabled,
            reminderHour = this[Keys.reminderHour] ?: d.reminderHour,
            reminderMinute = this[Keys.reminderMinute] ?: d.reminderMinute,
            budgetNotificationsEnabled = this[Keys.budgetNotifications] ?: d.budgetNotificationsEnabled,
            autoCloseMonthEnabled = this[Keys.autoCloseMonth] ?: d.autoCloseMonthEnabled,
            backupFrequency = enumOrDefault(this[Keys.backupFrequency], d.backupFrequency),
            backupFolderUri = this[Keys.backupFolderUri] ?: d.backupFolderUri,
            lastBackupAt = this[Keys.lastBackupAt] ?: d.lastBackupAt,
            keepBackupCount = this[Keys.keepBackupCount] ?: d.keepBackupCount,
            purgePreparedCutoff = this[Keys.purgePreparedCutoff] ?: d.purgePreparedCutoff,
            purgePreparedAt = this[Keys.purgePreparedAt] ?: d.purgePreparedAt,
            demoDataLoaded = this[Keys.demoDataLoaded] ?: d.demoDataLoaded,
            lastIntegrityCheckAt = this[Keys.lastIntegrityCheckAt] ?: d.lastIntegrityCheckAt,
            lastIntegrityOk = this[Keys.lastIntegrityOk] ?: d.lastIntegrityOk,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.onboardingDone] = s.onboardingDone
        this[Keys.ownerName] = s.ownerName
        this[Keys.themeMode] = s.themeMode.name
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.currencyCode] = s.currencyCode
        this[Keys.monthStartDay] = s.monthStartDay.coerceIn(AppSettings.MIN_START_DAY, AppSettings.MAX_START_DAY)
        this[Keys.retentionMonths] = s.retentionMonths
        this[Keys.retentionAction] = s.retentionAction.name
        this[Keys.pinEnabled] = s.pinEnabled
        this[Keys.pinHash] = s.pinHash
        this[Keys.pinSalt] = s.pinSalt
        this[Keys.biometricEnabled] = s.biometricEnabled
        this[Keys.dailyReminderEnabled] = s.dailyReminderEnabled
        this[Keys.reminderHour] = s.reminderHour
        this[Keys.reminderMinute] = s.reminderMinute
        this[Keys.budgetNotifications] = s.budgetNotificationsEnabled
        this[Keys.autoCloseMonth] = s.autoCloseMonthEnabled
        this[Keys.backupFrequency] = s.backupFrequency.name
        this[Keys.backupFolderUri] = s.backupFolderUri
        this[Keys.lastBackupAt] = s.lastBackupAt
        this[Keys.keepBackupCount] = s.keepBackupCount
        this[Keys.purgePreparedCutoff] = s.purgePreparedCutoff
        this[Keys.purgePreparedAt] = s.purgePreparedAt
        this[Keys.demoDataLoaded] = s.demoDataLoaded
        this[Keys.lastIntegrityCheckAt] = s.lastIntegrityCheckAt
        this[Keys.lastIntegrityOk] = s.lastIntegrityOk
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}

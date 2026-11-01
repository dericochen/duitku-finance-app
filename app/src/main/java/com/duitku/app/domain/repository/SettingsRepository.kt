package com.duitku.app.domain.repository

import com.duitku.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    /** Membaca, mengubah, lalu menulis seluruh pengaturan secara atomik. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

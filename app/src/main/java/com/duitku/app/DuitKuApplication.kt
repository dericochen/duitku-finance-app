package com.duitku.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.duitku.app.core.notification.NotificationChannels
import com.duitku.app.di.ApplicationScope
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class DuitKuApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        WorkScheduler.scheduleDefaults(this)
        // Jadwal backup otomatis & pengingat harian mengikuti pengaturan (termasuk setelah restore/reset).
        appScope.launch {
            settingsRepository.settings
                .map { Triple(it.backupFrequency, it.dailyReminderEnabled, it.reminderHour * 60 + it.reminderMinute) }
                .distinctUntilChanged()
                .collect { WorkScheduler.applySettings(this@DuitKuApplication, settingsRepository.current()) }
        }
    }
}

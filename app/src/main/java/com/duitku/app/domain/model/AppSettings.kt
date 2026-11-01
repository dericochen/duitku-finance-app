package com.duitku.app.domain.model

/** Seluruh pengaturan aplikasi (disimpan di DataStore). */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val ownerName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Default mati: tampilkan identitas merek DuitKu (Neo Finance) secara bawaan.
    // Pengguna tetap bisa menyalakan warna dinamis dari Pengaturan.
    val dynamicColor: Boolean = false,
    val currencyCode: String = "IDR",
    /** Tanggal awal periode bulanan (1..28). Contoh: 25 untuk tanggal gajian. */
    val monthStartDay: Int = 1,

    // Retensi data
    /** Masa retensi dalam bulan; [RETENTION_FOREVER] = selamanya. */
    val retentionMonths: Int = 12,
    val retentionAction: RetentionAction = RetentionAction.ASK_BEFORE_PURGE,

    // Keamanan
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,

    // Pengingat
    val dailyReminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val budgetNotificationsEnabled: Boolean = true,
    val autoCloseMonthEnabled: Boolean = true,

    // Backup
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val backupFolderUri: String = "",
    val lastBackupAt: Long = 0L,
    val keepBackupCount: Int = 5,

    // Persiapan pembersihan retensi: arsip + backup sudah dibuat untuk batas waktu ini
    val purgePreparedCutoff: Long = 0L,
    val purgePreparedAt: Long = 0L,

    // Lain-lain
    val demoDataLoaded: Boolean = false,
    val lastIntegrityCheckAt: Long = 0L,
    val lastIntegrityOk: Boolean = true,
) {
    val retentionForever: Boolean get() = retentionMonths == RETENTION_FOREVER

    companion object {
        const val RETENTION_FOREVER = 0
        val RETENTION_OPTIONS = listOf(12, 24, 36, RETENTION_FOREVER)
        const val MIN_START_DAY = 1
        const val MAX_START_DAY = 28

        fun retentionLabel(months: Int): String =
            if (months == RETENTION_FOREVER) "Selamanya" else "$months bulan"
    }
}

# DuitKu

Pencatat keuangan pribadi native Android (Kotlin, Jetpack Compose, Material 3, Room, Hilt). Offline-first, tanpa izin INTERNET.
Antarmuka berbahasa Indonesia, mata uang bawaan Rupiah ("Rp.xxx"), locale id-ID.



## Build & run

Prasyarat: Android Studio terbaru (mendukung AGP 8.13), JDK 17+ (JBR 21 bawaan Studio), Android SDK Platform 36 + Build-Tools 36.0.0.

1. Buka folder `DuitKu` di Android Studio, tunggu Gradle sync.
2. Pilih emulator/perangkat API 26+ lalu Run `app`.
3. Pada onboarding, nyalakan "Isi data contoh 12 bulan" untuk langsung mencoba grafik dan PDF.

Command line:

```
set ANDROID_HOME=C:\path\ke\Android\Sdk
gradlew.bat assembleDebug          # APK debug
gradlew.bat testDebugUnitTest      # 146 unit test (JVM + Robolectric)
gradlew.bat assembleRelease        # R8 + shrink resources (unsigned)
gradlew.bat connectedDebugAndroidTest   # uji migrasi database (perlu perangkat)
```

Hasil render PDF pada tes tersimpan sebagai PNG di `app/build/pdf-preview/` untuk diperiksa visual.

## Struktur proyek

```
app/src/main/java/com/duitku/app/
  core/            util (MoneyFormatter, DateFormats, PeriodCalculator), notification
  domain/          model, repository (antarmuka), usecase (murni, mudah diuji)
                   statement/ (PeriodMath, RunningBalance, Reconciliation, SnapshotFactory, Checksum)
                   report/ (ReportBuilder, AnnualReportBuilder)  budget/  stats/  planning/  retention/  security/
  data/            local (Room: entity, dao, DuitKuDatabase, Migrations), repository, backup, retention,
                   export, datastore, notification, seed (data contoh)
  export/          pdf (PageComposer, PdfTable, PdfCharts, QrDrawer, renderer), TabularExport (CSV/XLSX)
  di/              modul Hilt
  worker/          AutoClose, Recurring, DebtReminder, AutoBackup, Retention, DailyReminder + WorkScheduler
  presentation/    theme, components, navigation, security (kunci), screens/*
app/schemas/       skema Room yang diekspor (dipakai MigrationTest)
```


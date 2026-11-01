# DuitKu

Pencatat keuangan pribadi native Android (Kotlin, Jetpack Compose, Material 3, Room, Hilt). Offline-first, tanpa izin INTERNET.
Antarmuka berbahasa Indonesia, mata uang bawaan Rupiah ("Rp 1.250.000"), locale id-ID.

## Status

Semua 8 tahap selesai dan terverifikasi dengan build + unit test (146 tes, build bersih dari nol). Dokumen pendukung:

- [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md): error umum dan solusinya.
- [`docs/MANUAL_TEST_CHECKLIST.md`](docs/MANUAL_TEST_CHECKLIST.md): checklist uji manual termasuk simulasi 12 bulan.

| Tahap | Isi |
|---|---|
| 1 | Setup, Room (14 entity, DAO agregat, migrasi), Hilt, tema, navigasi |
| 2 | Dompet, kategori/subkategori, transaksi (CRUD, keypad, tag, cari/filter, swipe hapus + undo), onboarding |
| 3 | Dashboard, Mutasi bulanan (saldo berjalan), tutup bulan otomatis/manual, Arsip, audit log, verifikasi checksum |
| 4 | Anggaran (rollover, salin bulan lalu) + notifikasi 80%/100% |
| 5 | Statistik: donat, batang 6/12 bulan, tren saldo, Top 5, perbandingan, mode 12 bulan terakhir |
| 6 | Transaksi berulang (catch-up WorkManager), target tabungan, utang piutang + pengingat |
| 7 | PDF 14 bagian (3 tema), laporan tahunan/rentang, CSV, XLSX, riwayat ekspor, pratinjau, cetak, SAF, bagikan |
| 8 | PIN (PBKDF2) + biometrik, pengaturan, retensi aman, auto-backup, restore (timpa/gabung), reset berlapis |

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

## Keputusan desain

- **compileSdk/targetSdk 36, bukan 37.** AGP 8.13 mendukung maksimal API 36; library androidx terbaru butuh compileSdk 37 dan AGP 9.1+. Semua versi di `gradle/libs.versions.toml` sudah dicek lewat metadata AAR.
- **Nominal `Long` satuan utuh** (tanpa sen); pilihan mata uang hanya mengubah simbol.
- **Penamaan periode** dengan awal bulan N > 1: periode diberi nama bulan tempat ia berakhir (N=25: "November" = 25 Okt s.d. 24 Nov).
- **Saldo awal dompet punya `openedAt`.** Dompet baru di tengah periode memasukkan saldo awalnya sebagai arus "dompet baru", sehingga saldo akhir bulan lalu selalu sama dengan saldo awal bulan ini dan arsip tertutup tidak ikut berubah.
- **`StatementStatus` punya nilai ketiga `SUPERSEDED`** untuk revisi lama; tiap revisi adalah baris sendiri, snapshot + checksum lama tidak pernah diubah.
- **Bulan tertutup dibaca dari snapshot arsip**, bukan dari tabel transaksi. Karena itu tampilan Mutasi dan PDF bulan tertutup tetap utuh setelah retensi membersihkan transaksi mentah.
- **Retensi tidak pernah menghapus otomatis.** Urutan: tutup bulan > backup penuh > PDF laporan tahunan > tunggu konfirmasi > bersihkan. Efek transaksi yang dibersihkan dipindahkan ke saldo awal dompet dalam satu transaksi database sehingga saldo tidak berubah.
- **Hapus dompet/kategori yang sudah dipakai = diarsipkan.** Edit/hapus transaksi di bulan tertutup ditolak (`PeriodClosedException`); harus lewat "Buka Kembali Bulan" yang tercatat di audit log.
- **Grafik digambar dengan Canvas sendiri** (Compose di layar, android.graphics di PDF) tanpa pustaka chart pihak ketiga.
- **XLSX ditulis manual** (Office Open XML) agar tidak membawa Apache POI yang berat di Android.
- **Auto-tutup bulan memakai masa tenggang 3 hari** agar transaksi yang terlambat dicatat masih bisa dimasukkan.
- **Jam pengingat memakai WorkManager (perkiraan, tidak persis)**, bukan alarm persis; menghindari izin `SCHEDULE_EXACT_ALARM`.

## Keterbatasan yang diketahui

- Tampilan Compose belum dijalankan di emulator/perangkat selama pengembangan (tidak ada emulator di lingkungan build). Yang terverifikasi: kompilasi, logika (146 tes termasuk SQLite sungguhan), dan tata letak PDF (dirender ke PNG dan diperiksa). Gunakan checklist manual untuk UI.
- `android.graphics.pdf.PdfDocument` dan `PdfRenderer` (PDF asli + pratinjau) hanya bisa diuji di perangkat; di JVM, halaman dirender ke bitmap lewat antarmuka `PdfSink`.
- Anggaran dan statistik untuk bulan yang transaksi mentahnya sudah dibersihkan menghitung dari data yang tersisa (nol); arsip, Mutasi, dan PDF bulan tertutup tetap akurat karena memakai snapshot.
- Satu baris snapshot dibatasi ~2 MB oleh CursorWindow SQLite Android (setara puluhan ribu transaksi dalam satu bulan).
- Backup dari versi aplikasi yang lebih baru ditolak; migrasi isi backup antar-versi baru perlu ditambahkan bila skema berubah (saat ini hanya database v1).

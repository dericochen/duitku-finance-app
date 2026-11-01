# DuitKu (Data aman,Pengeluaran terkontrol,Hidup pun tentram)

Pencatat keuangan pribadi native Android (Kotlin, Jetpack Compose, Material 3, Room, Hilt). Offline-first, tanpa izin INTERNET,privacy aman,data aman.
Antarmuka berbahasa Indonesia, mata uang bawaan Rupiah ("Rp.XXX"), locale id-ID.

Identitas visual **Neo Finance**: kanvas cream, jangkar navy gelap, aksen lime — premium, minimal, dan rapi (lihat [Desain visual](#desain-visual-neo-finance)).

## Download

Unduh APK siap pasang: **[Download DuitKu (APK)](https://drive.google.com/file/d/1n4Ug2SqR0DuTZH13szR5LPJlN9A2Bh_l/view?usp=sharing)**

Cara memasang di perangkat Android:

1. Buka tautan di atas, lalu unduh berkas APK.
2. Jalankan APK. Bila muncul peringatan, izinkan **"Instal aplikasi dari sumber tidak dikenal"** untuk peramban/pengelola berkas yang Anda pakai (Settings → Apps → akses khusus → Install unknown apps).
3. Lanjutkan instalasi, lalu buka DuitKu dan ikuti onboarding.

Syarat: Android 8.0 (API 26) atau lebih baru. Semua data tersimpan di perangkat (offline, tanpa akun).

## Fitur

- **Transaksi** — pemasukan, pengeluaran, transfer antar dompet. Keypad nominal kustom, kategori + subkategori, catatan, tag (dengan saran), tanggal/waktu. CRUD penuh, geser-untuk-hapus + undo, validasi lengkap.
- **Pencarian & filter** — cari teks (catatan/kategori/dompet/tag/nominal) + filter jenis, rentang tanggal, dompet, kategori, tag, dan rentang nominal, dengan ringkasan total.
- **Dompet** — tipe Tunai/Bank/E-Wallet/Kartu Kredit/Tabungan, saldo awal, ikon & warna kustom, saldo terkini otomatis.
- **Kategori** — bertingkat (induk → sub), terpisah pemasukan/pengeluaran, ikon & warna, kategori default saat onboarding.
- **Anggaran** — bulanan per kategori/total, progres aman/waspada/melebihi, rollover, salin dari bulan lalu, notifikasi 80%/100%.
- **Statistik** — diagram donat, batang 6/12 bulan, tren saldo, Top 5, perbandingan antar periode.
- **Perencanaan** — transaksi berulang (harian/mingguan/bulanan/tahunan, catch-up WorkManager), target tabungan (estimasi tanggal & setoran per bulan), utang & piutang (cicilan, status, pengingat jatuh tempo).
- **Mutasi & Arsip** — tutup bulan (manual/otomatis) → snapshot read-only dengan nomor dokumen + checksum SHA-256, verifikasi integritas, buka kembali (revisi/superseded), audit log.
- **Ekspor** — PDF (3 tema, 14 bagian, QR verifikasi), CSV, XLSX; mutasi bulanan, rentang, laporan tahunan; pratinjau, riwayat, cetak, bagikan via SAF/FileProvider.
- **Backup & Restore** — backup terkodekan ke folder pilihan, otomatis (harian/mingguan/bulanan), inspect sebelum restore, mode timpa/gabung.
- **Retensi data** — 12/24/36 bulan atau selamanya; mode arsip-saja atau tanya-sebelum-bersihkan.
- **Keamanan** — kunci PIN (PBKDF2) + biometrik, auto-lock saat background.
- **Pengaturan** — tema terang/gelap/ikut sistem, warna dinamis opsional, nama pemilik, mata uang, tanggal awal periode (mis. tanggal gajian), pengingat harian, pemeriksa integritas database.



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
  presentation/    theme (Color, Theme, Type, Dimens — token Neo Finance), components (NeoCard, IconBadge, chart, keypad),
                   navigation, security (kunci), screens/*
app/schemas/       skema Room yang diekspor (dipakai MigrationTest)
```

## Desain visual (Neo Finance)

Identitas visual dipusatkan di `presentation/theme/` sehingga seluruh layar konsisten lewat theme tunggal — mengganti palet otomatis me-reskin seluruh aplikasi tanpa menyentuh logika.

**Palet**

| Token | Hex | Pemakaian |
|---|---|---|
| Primary background | `#F5F3EC` | Kanvas cream |
| Dark navy | `#172A3D` | Jangkar visual (kartu saldo, header) |
| Secondary navy | `#203D4D` | Permukaan navy terangkat |
| Accent lime | `#C5F36A` | CTA & interaksi penting |
| Secondary text | `#84909A` | Label/caption redup |
| Border | `#EAEAE3` | Garis tepi tipis |
| Income | `#79B96B` | Pemasukan |
| Expense | `#E58C79` | Pengeluaran |

**Prinsip**: kanvas cream (bukan putih generik), navy sebagai jangkar, lime untuk aksi penting, hierarki tipografi kuat, spasi konsisten, border tipis, bayangan ditahan, dan rounding selektif (kartu saldo 24–28dp, tombol 16–18dp, input 14–16dp, wadah ikon 14–16dp). Mode gelap memakai kanvas navy.

**Token** didefinisikan di:

- `theme/Color.kt` — palet Neo Finance.
- `theme/Theme.kt` — `ColorScheme` terang/gelap + `ExtendedColors` (accent, income, expense, navy, dll.).
- `theme/Type.kt` — hierarki tipografi + bentuk (`Shapes`).
- `theme/Dimens.kt` — skala spasi, radius komponen, elevasi, dan target sentuh minimum 48dp.

Komponen bersama (`components/`): `NeoCard`, `SectionHeader`, `IconBadge` (wadah ikon kotak-membulat), `DuitKuTopBar`, `AmountKeypad`, chart (`Charts.kt`), dan `BudgetProgress` — semuanya membaca token di atas.

> **Catatan**: warna dinamis (Material You) default **mati** agar identitas DuitKu tampil bawaan; pengguna tetap bisa menyalakannya dari Pengaturan.

**Perbaikan bug keypad nominal** — sebelumnya keypad nominal kustom selalu dipin di bawah sementara kolom Catatan/Tag memicu keyboard sistem, sehingga keduanya saling menimpa. Kini dipakai mode input eksklusif: keypad kustom hanya tampil saat "mode nominal" aktif dan otomatis disembunyikan begitu kolom teks mendapat fokus (`onFocusChanged`); mengetuk panel nominal menutup keyboard sistem. Keduanya tidak pernah muncul bersamaan.

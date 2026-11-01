# Error umum dan solusinya

| Gejala | Penyebab | Solusi |
|---|---|---|
| `SDK location not found` | `local.properties` tidak ada / path salah | Buka proyek di Android Studio (file dibuat otomatis), atau isi `sdk.dir=C\:\\path\\ke\\Android\\Sdk`. Atau set `ANDROID_HOME`. |
| `Failed to find Platform SDK with path: platforms;android-36` | Platform 36 belum terpasang | SDK Manager > SDK Platforms > Android 16 (API 36), lalu Build-Tools 36.0.0. |
| `Dependency requires libraries and applications that depend on it to compile against version 37` | Memperbarui library AndroidX ke rilis terbaru | Tetap pada versi di `gradle/libs.versions.toml` (semua dicek `minCompileSdk <= 36`), atau naik ke AGP 9.1+ dan `compileSdk = 37` bersamaan. |
| `Unsupported class file major version` / `Could not determine java version` | JDK terlalu lama | Gunakan JDK 17 atau 21 (Settings > Build Tools > Gradle > Gradle JDK = jbr-21). |
| `[ksp] ... could not be resolved` | Satu file sumber gagal dikompilasi sehingga KSP ikut gagal | Cari baris `e:` PERTAMA di output; perbaiki itu dahulu, error KSP lainnya hilang sendiri. |
| `Schema export directory is not provided` | Argumen KSP `room.schemaLocation` hilang | Sudah diatur di `app/build.gradle.kts`; jangan dihapus. |
| `A migration from X to Y was required but not found` | Versi database dinaikkan tanpa `Migration` | Tambahkan `Migration` di `DuitKuMigrations.ALL`, commit `schemas/<versi>.json`, jalankan `MigrationTest`. Jangan memakai `fallbackToDestructiveMigration`. |
| `Row too big to fit into CursorWindow` | Satu baris `snapshotJson` > ~2 MB (bulan dengan puluhan ribu transaksi) | Daftar arsip memakai proyeksi tanpa snapshot. Untuk bulan sangat besar, pecah snapshot atau simpan snapshot sebagai berkas; lihat catatan di `MonthlyStatementEntity`. |
| `FOREIGN KEY constraint failed` saat menghapus dompet | Dompet masih punya transaksi | Sengaja: dompet berisi transaksi hanya diarsipkan. |
| Notifikasi tidak muncul (Android 13+) | Izin `POST_NOTIFICATIONS` ditolak | Pengaturan sistem > Aplikasi > DuitKu > Notifikasi. |
| Backup otomatis tidak berjalan | Folder belum dipilih / izin folder hilang, atau penghemat baterai | Backup & Restore > Pilih folder. Matikan optimasi baterai untuk DuitKu. WorkManager bersifat perkiraan (tidak tepat waktu). |
| `java.lang.IllegalStateException: document is closed` pada tes PDF | `PdfDocument` butuh library native Android, tidak ada di JVM/Robolectric | Pembuatan PDF diabstraksikan lewat `PdfWriter`; tes memakai `FakePdfWriter` / `BitmapSink`. Uji PDF sungguhan di perangkat. |
| Tes DAO gagal `unable to open database` / `native` | Robolectric belum mengunduh android-all | Jalankan sekali dengan internet. Versi SDK tes dipatok `@Config(sdk = [34])`. |
| `The process cannot access the file ... classes.jar` (Windows) | Dua build Gradle berjalan bersamaan / antivirus mengunci berkas | `gradlew --stop` lalu ulangi; jangan jalankan dua build paralel di folder yang sama. |
| Layar kosong setelah restore | Restore mode Timpa mengganti seluruh data | Pulihkan lagi kondisi sebelumnya dari berkas `DuitKu_safety_*_pre_restore.duitku` di penyimpanan aplikasi. |
| Lupa PIN | PIN hanya disimpan sebagai hash | Tidak ada pemulihan PIN. Hapus data aplikasi (Pengaturan sistem > Penyimpanan) lalu pulihkan dari backup `.duitku`. |

# Checklist pengujian manual

Jalankan di emulator/perangkat API 26+ (idealnya satu perangkat API 26-28 dan satu API 34+). Tandai [x] bila lulus.
Bagian ini sengaja ditulis karena tampilan Compose belum pernah dijalankan di perangkat saat pengembangan; unit test hanya memverifikasi logika dan isi PDF.

## 0. Instalasi & onboarding
- [ ] Instal bersih; aplikasi membuka onboarding 4 langkah, tombol Kembali/Lanjut berfungsi, progres bergerak.
- [ ] Nama dompet kosong ditolak dengan pesan; pilih "Isi data contoh 12 bulan" lalu Selesai.
- [ ] Setelah selesai, muncul Beranda; izin notifikasi diminta (Android 13+).

## 1. Simulasi 12 bulan data
- [ ] Beranda: saldo total, pemasukan/pengeluaran bulan ini, kartu anggaran, grafik 7 hari, 5 transaksi terbaru, "12 bulan data tersimpan".
- [ ] Statistik > "12 Bulan Terakhir": 12 batang pemasukan vs pengeluaran, garis tren saldo, donat kategori, Top 5.
- [ ] Statistik > Mingguan/Bulanan/Tahunan/Custom: panah sebelumnya/berikutnya bekerja; Custom memilih dua tanggal; perbandingan "naik/turun %" tampil.
- [ ] Mutasi: ganti bulan mundur 11 kali tanpa lambat; filter dompet; saldo berjalan menurun/menaik logis; transfer berwarna biru.

## 2. Transaksi
- [ ] Tambah pengeluaran dengan keypad (000 dan hapus; tahan hapus = kosongkan); nominal kosong/0 ditolak.
- [ ] Transfer: dompet asal = tujuan ditolak; saldo kedua dompet berubah sesuai.
- [ ] Tag: ketik + selesai; saran tag muncul; hapus tag.
- [ ] Edit transaksi; hapus lewat ikon di form.
- [ ] Cari & filter: kata kunci, tipe, dompet, kategori, tanggal, rentang nominal; badge jumlah filter; Reset.
- [ ] Geser kiri untuk hapus > snackbar "Urungkan" mengembalikan transaksi.

## 3. Dompet & kategori
- [ ] Tambah/ubah dompet (jenis, ikon, warna). Kartu kredit boleh saldo awal utang.
- [ ] Hapus dompet yang punya transaksi: berubah menjadi diarsipkan, riwayat tetap.
- [ ] Kategori + subkategori; hapus kategori terpakai: diarsipkan; "Aktifkan" mengembalikan.

## 4. Anggaran & notifikasi
- [ ] Buat anggaran total + per kategori; progres hijau (<80%), kuning (80-99%), merah (>=100%).
- [ ] Catat pengeluaran sampai melewati 80% lalu 100%: tepat satu notifikasi per level.
- [ ] Rollover: aktifkan di bulan lalu, lihat "Termasuk sisa bulan lalu" di bulan ini.
- [ ] "Salin dari bulan sebelumnya" tidak menimpa anggaran yang sudah ada.

## 5. Tutup buku & arsip
- [ ] Mutasi > Tutup Bulan Ini: badge "Tertutup", nomor dokumen DKU-YYYYMM-0001, SHA-256 tampil.
- [ ] Coba tambah/ubah/hapus transaksi di bulan itu: ditolak dengan penjelasan.
- [ ] Buka Kembali Bulan (isi alasan): bisa edit; Audit Log mencatat REOPEN dan UPDATE dengan nilai lama/baru.
- [ ] Tutup lagi: revisi menjadi Rev.2, nomor -0002; Rev.1 tetap valid.
- [ ] Arsip Mutasi: cari bulan/nomor dokumen, filter tahun, Lihat, Export PDF, Bagikan, Verifikasi (berhasil).
- [ ] Saldo akhir bulan lalu = saldo awal bulan ini.

## 6. Transaksi berulang, target, utang
- [ ] Buat aturan harian mulai kemarin; matikan data seluler/ponsel 2 hari lalu buka lagi: transaksi yang terlewat tercatat sekali (tidak ganda).
- [ ] Target tabungan: tambah setoran, tarik dana, perkiraan tercapai, "perlu Rp X per bulan", konfeti pesan saat tercapai.
- [ ] Utang/piutang: bayar sebagian, lunas otomatis; jatuh tempo kemarin berstatus Terlambat; pengingat H-3/H-1/H.

## 7. Ekspor
- [ ] PDF bulan tertutup berstatus FINAL; bulan berjalan DRAFT (nama berkas berakhiran _DRAFT).
- [ ] Ketiga tema (Klasik/Modern/Hemat Tinta) terbaca; Hemat Tinta monokrom; opsi grafik mati memperpendek PDF.
- [ ] 14 bagian ada; header tabel diulang tiap halaman; "Hal. X dari Y" benar; teks Rupiah/huruf Indonesia benar.
- [ ] Pindai QR halaman terakhir: `DUITKU|nomor|hash` cocok dengan checksum yang tertulis.
- [ ] Isi saldo rekening bank: kolom selisih & OK/BEDA terisi.
- [ ] Pratinjau di dalam aplikasi, Cetak (PrintManager), Bagikan (WhatsApp/email/Drive), Simpan ke... (SAF).
- [ ] CSV dibuka di Excel/Sheets (huruf benar, rumus tidak dieksekusi); XLSX dibuka tanpa peringatan perbaikan.
- [ ] Laporan Tahunan 12 bulan; Riwayat Ekspor membuka ulang PDF lama; hapus berkas.
- [ ] Ekspor 3000+ transaksi (buat dengan data contoh berulang): progres bergerak, aplikasi tidak macet.

## 8. Keamanan & pengaturan
- [ ] Aktifkan PIN (PIN lemah 1234 ditolak); tutup aplikasi, buka: layar kunci; 5 PIN salah memicu jeda 30 detik yang tetap berlaku setelah aplikasi dimatikan paksa.
- [ ] Biometrik: aktifkan, buka lewat sidik jari; "Gunakan PIN" jatuh ke PIN.
- [ ] Ke latar belakang >30 detik lalu kembali: terkunci; <30 detik: tidak.
- [ ] Layar "aplikasi terbaru" menyembunyikan isi saat PIN aktif.
- [ ] Tema terang/gelap/ikuti sistem; warna dinamis; mata uang; tanggal awal bulan 25 mengubah periode.
- [ ] Pengingat harian muncul pada jam yang dipilih dan TIDAK muncul bila hari itu sudah ada transaksi.

## 9. Backup, restore, retensi
- [ ] Pilih folder, Cadangkan sekarang: berkas .duitku muncul; "Backup terakhir" di Beranda terbarui.
- [ ] Lakukan 6 backup otomatis/manual: hanya 5 terbaru tersisa di folder.
- [ ] Restore berkas valid mode Gabung (tidak menggandakan), mode Timpa (data sama persis). Berkas rusak/diubah ditolak dengan alasan; data tidak berubah.
- [ ] Retensi 12 bulan: tidak ada data hilang. Ubah ke 6 bulan: status menunjukkan transaksi kedaluwarsa; "Siapkan arsip" menutup bulan, membuat backup + PDF tahunan; baru setelah itu "Bersihkan" aktif.
- [ ] Setelah bersih: saldo dompet sama; bulan lama di Mutasi/Arsip tetap lengkap; PDF lama bisa dibuka.
- [ ] Mode "Pindahkan ke arsip saja": tombol bersihkan tidak ada.
- [ ] Reset semua data: 2 dialog + kata HAPUS (+PIN); kembali ke onboarding; backup pengaman tersimpan.
- [ ] Pengaturan > Penyimpanan menampilkan ukuran database, ekspor, backup.

## 10. Ketahanan
- [ ] Putar layar di form transaksi: isian tidak hilang.
- [ ] Mode pesawat: seluruh fitur tetap bekerja (tidak ada izin INTERNET).
- [ ] Instal ulang di atas versi lama (setelah ada migrasi) tidak menghapus data.

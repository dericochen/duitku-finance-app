package com.duitku.app.data.local

import androidx.room.migration.Migration

/**
 * Daftar migrasi database. Versi 1 adalah skema awal sehingga belum ada migrasi.
 *
 * Contoh pola saat menambah kolom di versi 2:
 *
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE transactions ADD COLUMN location TEXT NOT NULL DEFAULT ''")
 *     }
 * }
 * val ALL = arrayOf<Migration>(MIGRATION_1_2)
 * ```
 *
 * [validateChain] dipakai unit test untuk memastikan rantai migrasi tidak bolong dan
 * berakhir di [DuitKuDatabase.VERSION].
 */
object DuitKuMigrations {
    val ALL: Array<Migration> = arrayOf()

    /** Mengembalikan daftar pesan masalah; kosong berarti rantai valid. */
    fun validateChain(
        migrations: Array<Migration> = ALL,
        startVersion: Int = 1,
        targetVersion: Int = DuitKuDatabase.VERSION,
    ): List<String> {
        val problems = mutableListOf<String>()
        var current = startVersion
        val sorted = migrations.sortedBy { it.startVersion }
        for (migration in sorted) {
            if (migration.startVersion != current) {
                problems += "Migrasi hilang atau tidak berurutan: diharapkan mulai dari $current, ditemukan ${migration.startVersion}"
                current = migration.startVersion
            }
            if (migration.endVersion <= migration.startVersion) {
                problems += "Migrasi ${migration.startVersion}->${migration.endVersion} tidak menaikkan versi"
            }
            current = migration.endVersion
        }
        if (current != targetVersion) {
            problems += "Rantai migrasi berakhir di versi $current, padahal versi database $targetVersion"
        }
        return problems
    }
}

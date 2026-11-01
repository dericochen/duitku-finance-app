package com.duitku.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Menguji migrasi terhadap skema yang diekspor (app/schemas). Saat versi 2 dibuat, tambahkan
 * `helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2)` dan periksa data yang dibawa dari versi 1.
 *
 * Dijalankan di perangkat/emulator: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DuitKuDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun skemaVersi1DapatDibuatDanDivalidasi() {
        val db = helper.createDatabase(NAME, 1)
        db.execSQL(
            "INSERT INTO wallets (name, type, initialBalance, iconKey, colorArgb, isArchived, sortOrder, createdAt, openedAt) " +
                "VALUES ('Tunai', 'CASH', 1000, 'cash', 0, 0, 0, 0, 0)",
        )
        db.query("SELECT COUNT(*) FROM wallets").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.close()
        helper.runMigrationsAndValidate(NAME, 1, true)
    }

    @Test
    fun rantaiMigrasiLengkapSampaiVersiTerbaru() {
        assertTrue(DuitKuMigrations.validateChain().isEmpty())
        helper.createDatabase(NAME, 1).close()
        // Semua migrasi yang terdaftar harus mengantar skema versi 1 ke versi terbaru dan lolos validasi Room.
        helper.runMigrationsAndValidate(NAME, DuitKuDatabase.VERSION, true, *DuitKuMigrations.ALL)
    }

    private companion object {
        const val NAME = "migration-test"
    }
}

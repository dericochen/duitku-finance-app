package com.duitku.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuitKuMigrationsTest {

    private fun migration(from: Int, to: Int) = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    @Test
    fun `rantai migrasi aplikasi valid dan berakhir di versi database`() {
        val problems = DuitKuMigrations.validateChain()
        assertTrue("Rantai migrasi bermasalah: $problems", problems.isEmpty())
    }

    @Test
    fun `rantai berurutan dinyatakan valid`() {
        val problems = DuitKuMigrations.validateChain(
            migrations = arrayOf(migration(1, 2), migration(2, 3)),
            targetVersion = 3,
        )
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `migrasi yang bolong terdeteksi`() {
        val problems = DuitKuMigrations.validateChain(
            migrations = arrayOf(migration(1, 2), migration(3, 4)),
            targetVersion = 4,
        )
        assertEquals(1, problems.size)
        assertTrue(problems.first().contains("tidak berurutan"))
    }

    @Test
    fun `rantai yang tidak sampai versi target terdeteksi`() {
        val problems = DuitKuMigrations.validateChain(
            migrations = arrayOf(migration(1, 2)),
            targetVersion = 3,
        )
        assertEquals(1, problems.size)
    }

    @Test
    fun `versi database sesuai skema yang diekspor`() {
        assertEquals(1, DuitKuDatabase.VERSION)
    }
}

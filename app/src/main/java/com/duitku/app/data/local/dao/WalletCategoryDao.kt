package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert
    suspend fun insert(entity: WalletEntity): Long

    @Insert
    suspend fun insertAll(entities: List<WalletEntity>)

    @Update
    suspend fun update(entity: WalletEntity)

    @Delete
    suspend fun delete(entity: WalletEntity)

    @Query("SELECT * FROM wallets ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: Long): WalletEntity?

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observeById(id: Long): Flow<WalletEntity?>

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM wallets")
    suspend fun nextSortOrder(): Int

    @Query("UPDATE wallets SET initialBalance = initialBalance + :delta WHERE id = :walletId")
    suspend fun addToInitialBalance(walletId: Long, delta: Long)

    @Query("DELETE FROM wallets")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY kind ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE kind = :kind AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeByKind(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE kind = :kind")
    suspend fun nextSortOrder(kind: CategoryKind): Int

    /** Jumlah transaksi/anggaran yang memakai kategori (untuk mencegah penghapusan tak sengaja). */
    @Query(
        """SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId = :id OR subCategoryId = :id)
                + (SELECT COUNT(*) FROM budgets WHERE categoryId = :id)""",
    )
    suspend fun usageCount(id: Long): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

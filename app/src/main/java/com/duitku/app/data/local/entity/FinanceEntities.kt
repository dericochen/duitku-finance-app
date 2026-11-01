package com.duitku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.WalletType

@Serializable
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    /** Saldo awal. Bertambah otomatis (carry-forward) bila transaksi lama dibersihkan oleh retensi. */
    val initialBalance: Long,
    val iconKey: String,
    val colorArgb: Int,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    /** Saldo awal dihitung sejak waktu ini. Dompet yang dibuat di tengah periode memasukkan saldo awalnya sebagai arus "dompet baru". */
    val openedAt: Long = 0,
)

@Serializable
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId"), Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    /** null = kategori utama, terisi = subkategori dari kategori tersebut. */
    val parentId: Long? = null,
    val iconKey: String,
    val colorArgb: Int,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(WalletEntity::class, ["id"], ["walletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WalletEntity::class, ["id"], ["toWalletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(CategoryEntity::class, ["id"], ["subCategoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("walletId"), Index("toWalletId"), Index("categoryId"), Index("subCategoryId"),
        Index(value = ["isActive", "nextDueAt"]),
    ],
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TransactionType,
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val frequency: RecurrenceFrequency,
    /** Setiap N satuan frekuensi (mis. tiap 2 minggu). */
    val intervalCount: Int = 1,
    val startAt: Long,
    val endAt: Long? = null,
    /** Jadwal berikutnya yang belum dieksekusi. Dipakai untuk catch-up bila perangkat mati. */
    val nextDueAt: Long,
    val lastRunAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        // RESTRICT: dompet yang masih punya transaksi tidak bisa dihapus, hanya diarsipkan.
        ForeignKey(WalletEntity::class, ["id"], ["walletId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(WalletEntity::class, ["id"], ["toWalletId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(CategoryEntity::class, ["id"], ["subCategoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(RecurringTransactionEntity::class, ["id"], ["recurringId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("occurredAt"),
        Index("walletId"),
        Index("toWalletId"),
        Index("categoryId"),
        Index("subCategoryId"),
        Index("recurringId"),
        Index(value = ["type", "occurredAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    /** Selalu positif. Arah ditentukan oleh [type]. */
    val amount: Long,
    val walletId: Long,
    /** Dompet tujuan, hanya untuk TRANSFER. */
    val toWalletId: Long? = null,
    /** Kategori utama (null untuk TRANSFER). */
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    /** Waktu transaksi (epoch millis UTC). */
    val occurredAt: Long,
    val recurringId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("periodYm"), Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Periode dalam format YYYYMM (mis. 202611). */
    val periodYm: Int,
    /** null = anggaran total bulanan. */
    val categoryId: Long? = null,
    val amount: Long,
    /** Jika true, sisa anggaran periode ini ditambahkan ke anggaran kategori yang sama di periode berikutnya. */
    val rollover: Boolean = false,
)

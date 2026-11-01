package com.duitku.app.domain.usecase.category

import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.DefaultCategories
import com.duitku.app.domain.model.DefaultCategoryGroup
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(category: Category): UseCaseResult<Long> {
        val name = category.name.trim()
        if (name.isEmpty()) return UseCaseResult.Failure("Nama kategori wajib diisi")
        if (name.length > MAX_NAME) return UseCaseResult.Failure("Nama kategori maksimal $MAX_NAME karakter")

        if (category.parentId != null) {
            val parent = repository.get(category.parentId)
                ?: return UseCaseResult.Failure("Kategori induk tidak ditemukan")
            if (parent.parentId != null) return UseCaseResult.Failure("Subkategori tidak boleh bersarang lebih dari satu tingkat")
            if (parent.kind != category.kind) return UseCaseResult.Failure("Jenis subkategori harus sama dengan kategori induk")
            if (category.id != 0L && repository.hasChildren(category.id)) {
                return UseCaseResult.Failure("Kategori yang memiliki subkategori tidak bisa dijadikan subkategori")
            }
        }

        val siblings = repository.observeAll().first()
            .filter { it.kind == category.kind && it.parentId == category.parentId && it.id != category.id }
        if (siblings.any { it.name.equals(name, ignoreCase = true) }) {
            return UseCaseResult.Failure("Sudah ada kategori \"$name\" pada tingkat yang sama")
        }
        return try {
            UseCaseResult.Success(repository.save(category.copy(name = name)))
        } catch (e: Exception) {
            UseCaseResult.Failure(e.message ?: "Gagal menyimpan kategori")
        }
    }

    companion object {
        const val MAX_NAME = 30
    }
}

sealed interface DeleteCategoryResult {
    data object Deleted : DeleteCategoryResult

    /** Kategori dipakai transaksi/anggaran, jadi hanya diarsipkan. */
    data class Archived(val usage: Int) : DeleteCategoryResult
    data class Failed(val message: String) : DeleteCategoryResult
}

class DeleteCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: Long): DeleteCategoryResult = try {
        val children = repository.observeAll().first().filter { it.parentId == id }
        val usage = repository.usageCount(id) + children.sumOf { repository.usageCount(it.id) }
        if (usage > 0) {
            repository.setArchived(id, true)
            DeleteCategoryResult.Archived(usage)
        } else {
            repository.delete(id)
            DeleteCategoryResult.Deleted
        }
    } catch (e: Exception) {
        DeleteCategoryResult.Failed(e.message ?: "Gagal menghapus kategori")
    }
}

/** Membuat kategori bawaan terpilih (induk lalu subkategorinya). Melewati nama yang sudah ada. */
class SeedDefaultCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    /** @param selectedKeys kunci "KIND:Nama" dari [key]; null = semua kategori bawaan. */
    suspend operator fun invoke(selectedKeys: Set<String>? = null): Int {
        val existing = repository.observeAll().first()
        var created = 0
        for (group in DefaultCategories.all) {
            if (selectedKeys != null && key(group) !in selectedKeys) continue
            if (existing.any { it.parentId == null && it.kind == group.kind && it.name.equals(group.name, true) }) continue
            val parentId = repository.save(
                Category(
                    name = group.name,
                    kind = group.kind,
                    iconKey = group.iconKey,
                    colorArgb = group.colorArgb,
                    isDefault = true,
                ),
            )
            created++
            group.subcategories.forEach { (subName, subIcon) ->
                repository.save(
                    Category(
                        name = subName,
                        kind = group.kind,
                        parentId = parentId,
                        iconKey = subIcon,
                        colorArgb = group.colorArgb,
                        isDefault = true,
                    ),
                )
                created++
            }
        }
        return created
    }

    companion object {
        fun key(group: DefaultCategoryGroup): String = "${group.kind.name}:${group.name}"
    }
}

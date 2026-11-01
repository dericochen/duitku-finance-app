package com.duitku.app.data.repository

import com.duitku.app.data.local.dao.CategoryDao
import com.duitku.app.data.mapper.toDomain
import com.duitku.app.data.mapper.toEntity
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeTree(kind: CategoryKind): Flow<List<CategoryNode>> =
        dao.observeAll().map { list ->
            val all = list.map { it.toDomain() }.filter { it.kind == kind && !it.isArchived }
            val childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId }
            all.filter { it.parentId == null }.map { parent ->
                CategoryNode(parent, childrenByParent[parent.id].orEmpty())
            }
        }

    override suspend fun get(id: Long): Category? = dao.getById(id)?.toDomain()

    override suspend fun count(): Int = dao.count()

    override suspend fun save(category: Category): Long {
        return if (category.id == 0L) {
            val order = dao.nextSortOrder(category.kind)
            dao.insert(category.copy(sortOrder = order).toEntity())
        } else {
            dao.update(category.toEntity())
            category.id
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isArchived = archived))
        // Arsipkan/aktifkan subkategori bersama induknya agar konsisten.
        dao.getAll().filter { it.parentId == id }.forEach { dao.update(it.copy(isArchived = archived)) }
    }

    override suspend fun usageCount(id: Long): Int = dao.usageCount(id)

    override suspend fun hasChildren(id: Long): Boolean = dao.countChildren(id) > 0

    override suspend fun delete(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.delete(existing)
    }

    override suspend fun insertAll(categories: List<Category>): List<Long> =
        dao.insertAll(categories.map { it.toEntity() })
}

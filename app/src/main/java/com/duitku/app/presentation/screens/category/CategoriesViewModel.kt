package com.duitku.app.presentation.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val tree: List<CategoryNode> = emptyList(),
    val archived: List<Category> = emptyList(),
    val expandedIds: Set<Long> = emptySet(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<CategoriesUiState> = combine(kind, expanded, repository.observeAll()) { k, exp, all ->
        val ofKind = all.filter { it.kind == k }
        val active = ofKind.filter { !it.isArchived }
        val childrenByParent = active.filter { it.parentId != null }.groupBy { it.parentId }
        CategoriesUiState(
            isLoading = false,
            kind = k,
            tree = active.filter { it.parentId == null }.map { CategoryNode(it, childrenByParent[it.id].orEmpty()) },
            archived = ofKind.filter { it.isArchived && it.parentId == null },
            expandedIds = exp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun selectKind(newKind: CategoryKind) {
        kind.value = newKind
    }

    fun toggleExpanded(id: Long) {
        expanded.update { if (id in it) it - id else it + id }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repository.setArchived(id, false) }
    }
}

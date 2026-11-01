package com.duitku.app.presentation.screens.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.Palette
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.usecase.category.DeleteCategoryResult
import com.duitku.app.domain.usecase.category.DeleteCategoryUseCase
import com.duitku.app.domain.usecase.category.SaveCategoryUseCase
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryEditUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val parentName: String? = null,
    val name: String = "",
    val iconKey: String = "other",
    val colorArgb: Int = Palette.default,
    val nameError: String? = null,
    val isSaving: Boolean = false,
) {
    val isSub: Boolean get() = parentName != null
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
    private val saveCategory: SaveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {

    private val categoryId = savedStateHandle.get<Long>(Screen.CategoryEdit.ARG_ID)?.takeIf { it > 0 } ?: 0L
    private val argParentId = savedStateHandle.get<Long>(Screen.CategoryEdit.ARG_PARENT)?.takeIf { it > 0 }
    private val argKind = savedStateHandle.get<String>(Screen.CategoryEdit.ARG_KIND)
        ?.let { name -> CategoryKind.entries.firstOrNull { it.name == name } } ?: CategoryKind.EXPENSE

    private val _state = MutableStateFlow(CategoryEditUiState(isEdit = categoryId != 0L, kind = argKind))
    val state: StateFlow<CategoryEditUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var parentId: Long? = argParentId
    private var original: Category? = null

    init {
        viewModelScope.launch {
            if (categoryId != 0L) {
                val category = repository.get(categoryId)
                if (category == null) {
                    _events.send(UiEvent.ShowMessage("Kategori tidak ditemukan"))
                    _events.send(UiEvent.NavigateBack)
                    return@launch
                }
                original = category
                parentId = category.parentId
            }
            val parent = parentId?.let { repository.get(it) }
            _state.update {
                val base = original
                it.copy(
                    isLoading = false,
                    kind = base?.kind ?: parent?.kind ?: argKind,
                    parentName = parent?.name,
                    name = base?.name.orEmpty(),
                    iconKey = base?.iconKey ?: parent?.iconKey ?: "other",
                    colorArgb = base?.colorArgb ?: parent?.colorArgb ?: Palette.default,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(SaveCategoryUseCase.MAX_NAME + 5), nameError = null) }
    fun onIconChange(key: String) = _state.update { it.copy(iconKey = key) }
    fun onColorChange(argb: Int) = _state.update { it.copy(colorArgb = argb) }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val category = Category(
                id = categoryId,
                name = s.name,
                kind = s.kind,
                parentId = parentId,
                iconKey = s.iconKey,
                colorArgb = s.colorArgb,
                isDefault = original?.isDefault ?: false,
                isArchived = original?.isArchived ?: false,
                sortOrder = original?.sortOrder ?: 0,
            )
            when (val result = saveCategory(category)) {
                is UseCaseResult.Success -> {
                    _events.send(UiEvent.ShowMessage("Kategori disimpan"))
                    _events.send(UiEvent.NavigateBack)
                }
                is UseCaseResult.Failure -> _state.update { it.copy(isSaving = false, nameError = result.message) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            when (val result = deleteCategory(categoryId)) {
                DeleteCategoryResult.Deleted -> {
                    _events.send(UiEvent.ShowMessage("Kategori dihapus"))
                    _events.send(UiEvent.NavigateBack)
                }
                is DeleteCategoryResult.Archived -> {
                    _events.send(UiEvent.ShowMessage("Kategori dipakai ${result.usage} data, jadi diarsipkan (riwayat tetap aman)"))
                    _events.send(UiEvent.NavigateBack)
                }
                is DeleteCategoryResult.Failed -> _events.send(UiEvent.ShowMessage(result.message))
            }
        }
    }
}

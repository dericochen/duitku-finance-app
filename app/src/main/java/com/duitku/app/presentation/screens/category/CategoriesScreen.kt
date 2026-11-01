package com.duitku.app.presentation.screens.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onAdd: (kind: CategoryKind, parentId: Long?) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesContent(
        state = state,
        onBack = onBack,
        onKindChange = viewModel::selectKind,
        onToggleExpand = viewModel::toggleExpanded,
        onAdd = onAdd,
        onEdit = onEdit,
        onRestore = viewModel::restore,
    )
}

@Composable
fun CategoriesContent(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onKindChange: (CategoryKind) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onAdd: (CategoryKind, Long?) -> Unit,
    onEdit: (Long) -> Unit,
    onRestore: (Long) -> Unit,
) {
    Scaffold(
        topBar = { DuitKuTopBar(title = "Kategori", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(state.kind, null) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah kategori") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = CategoryKind.entries.indexOf(state.kind)) {
                CategoryKind.entries.forEach { kind ->
                    Tab(
                        selected = state.kind == kind,
                        onClick = { onKindChange(kind) },
                        text = { Text(kind.label) },
                    )
                }
            }
            when {
                state.isLoading -> LoadingBox()
                state.tree.isEmpty() && state.archived.isEmpty() -> EmptyState(
                    title = "Belum ada kategori",
                    message = "Buat kategori ${state.kind.label.lowercase()} untuk mengelompokkan transaksi Anda.",
                    actionLabel = "Tambah kategori",
                    onAction = { onAdd(state.kind, null) },
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.tree, key = { it.category.id }) { node ->
                        CategoryNodeItem(
                            node = node,
                            expanded = node.category.id in state.expandedIds,
                            onToggle = { onToggleExpand(node.category.id) },
                            onEdit = onEdit,
                            onAddSub = { onAdd(state.kind, node.category.id) },
                        )
                    }
                    if (state.archived.isNotEmpty()) {
                        item(key = "archived") {
                            Text(
                                "Diarsipkan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(state.archived, key = { "a${it.id}" }) { category ->
                            ListItem(
                                leadingContent = { IconBadge(category.iconKey, category.colorArgb) },
                                headlineContent = { Text(category.name) },
                                trailingContent = { TextButton(onClick = { onRestore(category.id) }) { Text("Aktifkan") } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryNodeItem(
    node: CategoryNode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddSub: () -> Unit,
) {
    Column {
        ListItem(
            modifier = Modifier.clickable(onClick = if (node.children.isNotEmpty()) onToggle else ({ onEdit(node.category.id) })),
            leadingContent = { IconBadge(node.category.iconKey, node.category.colorArgb) },
            headlineContent = { Text(node.category.name) },
            supportingContent = {
                if (node.children.isNotEmpty()) Text("${node.children.size} subkategori")
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEdit(node.category.id) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Ubah ${node.category.name}")
                    }
                    if (node.children.isNotEmpty()) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Tutup subkategori" else "Buka subkategori",
                        )
                    }
                }
            },
        )
        if (expanded) {
            node.children.forEach { child ->
                ListItem(
                    modifier = Modifier
                        .padding(start = 32.dp)
                        .clickable { onEdit(child.id) },
                    leadingContent = { IconBadge(child.iconKey, child.colorArgb, size = 32.dp) },
                    headlineContent = { Text(child.name) },
                )
            }
        }
        if (expanded || node.children.isEmpty()) {
            TextButton(onClick = onAddSub, modifier = Modifier.padding(start = 56.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Subkategori", modifier = Modifier.padding(start = 4.dp))
            }
        }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoriesPreview() {
    val food = Category(1, "Makanan", CategoryKind.EXPENSE, null, "food", 0xFFE65100.toInt())
    DuitKuTheme(dynamicColor = false) {
        CategoriesContent(
            state = CategoriesUiState(
                isLoading = false,
                tree = listOf(
                    CategoryNode(food, listOf(Category(2, "Kopi & jajan", CategoryKind.EXPENSE, 1, "coffee", 0xFFE65100.toInt()))),
                    CategoryNode(Category(3, "Transport", CategoryKind.EXPENSE, null, "transport", 0xFF0277BD.toInt()), emptyList()),
                ),
                expandedIds = setOf(1L),
            ),
            onBack = {}, onKindChange = {}, onToggleExpand = {}, onAdd = { _, _ -> }, onEdit = {}, onRestore = {},
        )
    }
}

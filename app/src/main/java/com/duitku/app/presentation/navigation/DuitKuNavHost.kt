package com.duitku.app.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.presentation.components.PlaceholderScreen
import com.duitku.app.presentation.screens.archive.ArchiveScreen
import com.duitku.app.presentation.screens.archive.AuditLogScreen
import com.duitku.app.presentation.screens.budget.BudgetScreen
import com.duitku.app.presentation.screens.backup.BackupScreen
import com.duitku.app.presentation.screens.backup.RetentionScreen
import com.duitku.app.presentation.screens.category.CategoriesScreen
import com.duitku.app.presentation.screens.debts.DebtsScreen
import com.duitku.app.presentation.screens.export.ExportCenterScreen
import com.duitku.app.presentation.screens.export.ExportHistoryScreen
import com.duitku.app.presentation.screens.export.PdfPreviewScreen
import com.duitku.app.presentation.screens.goals.GoalsScreen
import com.duitku.app.presentation.screens.home.HomeScreen
import com.duitku.app.presentation.screens.recurring.RecurringEditScreen
import com.duitku.app.presentation.screens.recurring.RecurringListScreen
import com.duitku.app.presentation.screens.settings.SettingsScreen
import com.duitku.app.presentation.screens.statement.StatementScreen
import com.duitku.app.presentation.screens.stats.StatsScreen
import com.duitku.app.presentation.screens.category.CategoryEditScreen
import com.duitku.app.presentation.screens.more.MoreScreen
import com.duitku.app.presentation.screens.onboarding.OnboardingScreen
import com.duitku.app.presentation.screens.transaction.TransactionEditScreen
import com.duitku.app.presentation.screens.transaction.TransactionSearchScreen
import com.duitku.app.presentation.screens.wallet.WalletEditScreen
import com.duitku.app.presentation.screens.wallet.WalletsScreen

private const val ANIM_MS = 260

@Composable
fun DuitKuNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Transisi halus: tab utama memudar, layar detail bergeser.
        enterTransition = { slideInHorizontally(tween(ANIM_MS)) { it / 8 } + fadeIn(tween(ANIM_MS)) },
        exitTransition = { fadeOut(tween(ANIM_MS / 2)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_MS)) { it / 8 } + fadeOut(tween(ANIM_MS)) },
    ) {
        // ----- Alur awal -----
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // ----- Tab utama -----
        composable(Screen.Home.route, enterTransition = { fadeIn(tween(ANIM_MS)) }, exitTransition = { fadeOut(tween(ANIM_MS / 2)) }) {
            HomeScreen(
                onAddTransaction = { type -> go(Screen.TransactionEdit.create(type = type.name)) },
                onOpenTransaction = { id -> go(Screen.TransactionEdit.create(id = id)) },
                onSeeAll = { navController.navigate(Screen.Statement.route) { launchSingleTop = true } },
                onOpenBudget = { navController.navigate(Screen.Budget.route) { launchSingleTop = true } },
                onOpenBackup = { go(Screen.Backup.route) },
            )
        }
        composable(Screen.Statement.route, enterTransition = { fadeIn(tween(ANIM_MS)) }, exitTransition = { fadeOut(tween(ANIM_MS / 2)) }) {
            StatementScreen(
                onOpenTransaction = { id -> go(Screen.TransactionEdit.create(id = id)) },
                onSearch = { go(Screen.TransactionSearch.route) },
                onExport = { period -> go(Screen.ExportCenter.create(period)) },
                onAudit = { go(Screen.AuditLog.route) },
            )
        }
        composable(Screen.Budget.route, enterTransition = { fadeIn(tween(ANIM_MS)) }, exitTransition = { fadeOut(tween(ANIM_MS / 2)) }) {
            BudgetScreen()
        }
        composable(Screen.Stats.route, enterTransition = { fadeIn(tween(ANIM_MS)) }, exitTransition = { fadeOut(tween(ANIM_MS / 2)) }) {
            StatsScreen()
        }
        composable(
            Screen.More.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            MoreScreen(onNavigate = go)
        }

        // ----- Transaksi -----
        composable(
            route = Screen.TransactionEdit.route,
            arguments = listOf(
                navArgument(Screen.TransactionEdit.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Screen.TransactionEdit.ARG_TYPE) { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            TransactionEditScreen(onBack = back)
        }
        composable(Screen.TransactionSearch.route) {
            TransactionSearchScreen(
                onBack = back,
                onOpenTransaction = { id -> go(Screen.TransactionEdit.create(id = id)) },
            )
        }

        // ----- Dompet & kategori -----
        composable(Screen.Wallets.route) {
            WalletsScreen(
                onBack = back,
                onAdd = { go(Screen.WalletEdit.create()) },
                onEdit = { id -> go(Screen.WalletEdit.create(id)) },
            )
        }
        composable(
            route = Screen.WalletEdit.route,
            arguments = listOf(navArgument(Screen.WalletEdit.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) {
            WalletEditScreen(onBack = back)
        }
        composable(Screen.Categories.route) {
            CategoriesScreen(
                onBack = back,
                onAdd = { kind: CategoryKind, parentId: Long? ->
                    go(Screen.CategoryEdit.create(parentId = parentId, kind = kind.name))
                },
                onEdit = { id -> go(Screen.CategoryEdit.create(id = id)) },
            )
        }
        composable(
            route = Screen.CategoryEdit.route,
            arguments = listOf(
                navArgument(Screen.CategoryEdit.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Screen.CategoryEdit.ARG_PARENT) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Screen.CategoryEdit.ARG_KIND) { type = NavType.StringType; defaultValue = "EXPENSE" },
            ),
        ) {
            CategoryEditScreen(onBack = back)
        }

        // ----- Arsip & ekspor -----
        composable(Screen.Archive.route) {
            ArchiveScreen(
                onBack = back,
                onView = { period -> go(Screen.StatementDetail.create(period)) },
                onExport = { period -> go(Screen.ExportCenter.create(period)) },
                onShare = { period -> go(Screen.ExportCenter.create(period)) },
            )
        }
        composable(
            route = Screen.StatementDetail.route,
            arguments = listOf(navArgument(Screen.StatementDetail.ARG_PERIOD) { type = NavType.IntType }),
        ) {
            StatementScreen(
                onBack = back,
                onOpenTransaction = { id -> go(Screen.TransactionEdit.create(id = id)) },
                onSearch = { go(Screen.TransactionSearch.route) },
                onExport = { period -> go(Screen.ExportCenter.create(period)) },
                onAudit = { go(Screen.AuditLog.route) },
            )
        }
        composable(
            route = Screen.ExportCenter.route,
            arguments = listOf(navArgument(Screen.ExportCenter.ARG_PERIOD) { type = NavType.IntType; defaultValue = -1 }),
        ) {
            ExportCenterScreen(
                onBack = back,
                onPreview = { id -> go(Screen.PdfPreview.create(id)) },
                onHistory = { go(Screen.ExportHistory.route) },
            )
        }
        composable(Screen.ExportHistory.route) {
            ExportHistoryScreen(onBack = back, onPreview = { id -> go(Screen.PdfPreview.create(id)) })
        }
        composable(
            route = Screen.PdfPreview.route,
            arguments = listOf(navArgument(Screen.PdfPreview.ARG_ID) { type = NavType.LongType }),
        ) { PdfPreviewScreen(onBack = back) }
        // ----- Perencanaan -----
        composable(Screen.Recurring.route) {
            RecurringListScreen(onBack = back, onAdd = { go(Screen.RecurringEdit.create()) }, onEdit = { id -> go(Screen.RecurringEdit.create(id)) })
        }
        composable(
            route = Screen.RecurringEdit.route,
            arguments = listOf(navArgument(Screen.RecurringEdit.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) { RecurringEditScreen(onBack = back) }
        composable(Screen.Goals.route) { GoalsScreen(onBack = back) }
        composable(Screen.Debts.route) { DebtsScreen(onBack = back) }

        // ----- Pengaturan & data -----
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = back, onRetention = { go(Screen.Retention.route) }, onBackup = { go(Screen.Backup.route) })
        }
        composable(Screen.Backup.route) { BackupScreen(onBack = back) }
        composable(Screen.Retention.route) { RetentionScreen(onBack = back) }
        composable(Screen.AuditLog.route) { AuditLogScreen(onBack = back) }
    }
}

private fun NavGraphBuilder.topLevel(screen: Screen, title: String, description: String) {
    composable(
        route = screen.route,
        // Perpindahan antar tab cukup memudar agar terasa ringan.
        enterTransition = { fadeIn(tween(ANIM_MS)) },
        exitTransition = { fadeOut(tween(ANIM_MS / 2)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { fadeOut(tween(ANIM_MS / 2)) },
    ) {
        PlaceholderScreen(title = title, description = description)
    }
}

private fun NavGraphBuilder.detail(
    screen: Screen,
    title: String,
    description: String,
    onBack: () -> Unit,
    args: List<NamedNavArgument> = emptyList(),
) {
    composable(route = screen.route, arguments = args) {
        PlaceholderScreen(title = title, description = description, onBack = onBack)
    }
}

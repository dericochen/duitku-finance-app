package com.duitku.app.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.duitku.app.R

/** Lima tab bottom navigation: Beranda, Mutasi, Anggaran, Statistik, Lainnya. */
enum class BottomNavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Screen.Home, R.string.nav_home, Icons.Rounded.Home),
    STATEMENT(Screen.Statement, R.string.nav_statement, Icons.AutoMirrored.Rounded.ReceiptLong),
    BUDGET(Screen.Budget, R.string.nav_budget, Icons.Rounded.AccountBalanceWallet),
    STATS(Screen.Stats, R.string.nav_stats, Icons.Rounded.BarChart),
    MORE(Screen.More, R.string.nav_more, Icons.Rounded.MoreHoriz),
}

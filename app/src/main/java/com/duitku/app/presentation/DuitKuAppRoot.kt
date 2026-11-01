package com.duitku.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duitku.app.R
import com.duitku.app.domain.model.ThemeMode
import com.duitku.app.presentation.navigation.BottomNavItem
import com.duitku.app.presentation.navigation.DuitKuNavHost
import com.duitku.app.presentation.navigation.Screen
import com.duitku.app.presentation.security.LockScreen
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun DuitKuAppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val integrityProblem by viewModel.integrityProblem.collectAsStateWithLifecycle()

    DuitKuTheme(
        themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = settings?.dynamicColor ?: false,
    ) {
        val loaded = settings
        if (loaded == null) {
            // Pengaturan belum terbaca: tampilkan latar kosong sesaat, bukan onboarding palsu.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            // Tujuan awal ditentukan sekali saat pengaturan pertama kali tersedia.
            val startDestination = remember {
                if (loaded.onboardingDone) Screen.Home.route else Screen.Onboarding.route
            }
            Box(Modifier.fillMaxSize()) {
                MainScaffold(startDestination = startDestination, onboardingDone = loaded.onboardingDone)
                // Layar kunci menutupi seluruh aplikasi dan menelan semua sentuhan di bawahnya.
                val locked by viewModel.lock.locked.collectAsStateWithLifecycle()
                if (loaded.pinEnabled && locked) {
                    LockScreen(
                        lock = viewModel.lock, biometricEnabled = loaded.biometricEnabled,
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                    )
                }
            }
        }

        integrityProblem?.let { problem ->
            AlertDialog(
                onDismissRequest = viewModel::dismissIntegrityProblem,
                title = { Text("Peringatan integritas data") },
                text = {
                    Text(
                        "$problem.\n\nSegera buat backup dari menu Lainnya > Backup & Restore, " +
                            "lalu pulihkan dari backup terakhir bila masalah berlanjut.",
                    )
                },
                confirmButton = { TextButton(onClick = viewModel::dismissIntegrityProblem) { Text("Mengerti") } },
            )
        }
    }
}

@Composable
private fun MainScaffold(startDestination: String, onboardingDone: Boolean) {
    RequestNotificationPermissionOnce()
    val navController = rememberNavController()
    // Setelah reset data, kembali ke onboarding dan buang seluruh riwayat navigasi.
    LaunchedEffect(onboardingDone) {
        if (!onboardingDone && navController.currentDestination?.route != Screen.Onboarding.route) {
            navController.navigate(Screen.Onboarding.route) { popUpTo(0) { inclusive = true } }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // Layar di dalam NavHost mengurus inset sistemnya sendiri (TopAppBar/Scaffold bersarang).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in Screen.topLevelRoutes) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    BottomNavItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute in Screen.fabRoutes) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.TransactionEdit.create()) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add_transaction))
                }
            }
        },
    ) { padding ->
        DuitKuNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

/** Android 13+: minta izin notifikasi sekali setelah onboarding (anggaran, jatuh tempo, pengingat, backup). */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
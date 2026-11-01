package com.duitku.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.presentation.DuitKuAppRoot
import com.duitku.app.presentation.security.AppLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * FragmentActivity (bukan ComponentActivity biasa) karena BiometricPrompt membutuhkannya.
 * Compose tetap dipakai lewat setContent.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lock: AppLockManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { DuitKuAppRoot() }

        // Saat PIN aktif: sembunyikan isi aplikasi dari tampilan "aplikasi terbaru" dan tangkapan layar.
        lifecycleScope.launch {
            settingsRepository.settings.map { it.pinEnabled }.distinctUntilChanged().collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground()
    }

    override fun onStop() {
        lock.onBackground()
        super.onStop()
    }
}

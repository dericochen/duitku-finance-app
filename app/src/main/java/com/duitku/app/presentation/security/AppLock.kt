package com.duitku.app.presentation.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.security.PinAttemptPolicy
import com.duitku.app.domain.usecase.security.PinHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PinCheck {
    data object Success : PinCheck
    data class Wrong(val failures: Int, val lockedForMillis: Long) : PinCheck
    data class Locked(val remainingMillis: Long) : PinCheck
}

/**
 * Status kunci aplikasi. Terkunci saat proses mulai dan setelah aplikasi di latar belakang lebih dari [AUTO_LOCK_MS].
 * Jumlah percobaan salah disimpan di SharedPreferences sehingga mematikan paksa aplikasi tidak mereset jeda.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L

    fun onBackground(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    fun onForeground(now: Long = System.currentTimeMillis()) {
        if (backgroundedAt > 0 && now - backgroundedAt >= AUTO_LOCK_MS) _locked.value = true
        backgroundedAt = 0
    }

    fun unlock() {
        _locked.value = false
        resetFailures()
    }

    /** Dipakai setelah pengguna baru mengaktifkan PIN agar tidak langsung terkunci. */
    fun markUnlocked() {
        _locked.value = false
    }

    fun remainingLockMillis(now: Long = System.currentTimeMillis()): Long =
        PinAttemptPolicy.remainingLockMillis(prefs.getInt(KEY_FAILURES, 0), prefs.getLong(KEY_LAST_FAIL, 0L), now)

    suspend fun check(pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        val remaining = remainingLockMillis(now)
        if (remaining > 0) return PinCheck.Locked(remaining)
        val s = settingsRepository.current()
        if (PinHasher.verify(pin, s.pinHash, s.pinSalt)) {
            unlock()
            return PinCheck.Success
        }
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_LAST_FAIL, now).apply()
        return PinCheck.Wrong(failures, PinAttemptPolicy.lockoutSeconds(failures) * 1_000)
    }

    /** Memverifikasi PIN tanpa mengubah status kunci (untuk aksi sensitif seperti ganti/hapus PIN). */
    suspend fun verifyOnly(pin: String): Boolean {
        val s = settingsRepository.current()
        return PinHasher.verify(pin, s.pinHash, s.pinSalt)
    }

    private fun resetFailures() {
        prefs.edit().remove(KEY_FAILURES).remove(KEY_LAST_FAIL).apply()
    }

    companion object {
        const val AUTO_LOCK_MS = 30_000L
        private const val KEY_FAILURES = "failures"
        private const val KEY_LAST_FAIL = "last_failure"
    }
}

object BiometricHelper {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit, onFailure: (String) -> Unit = {}) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setNegativeButtonText("Gunakan PIN")
            .build()
        prompt.authenticate(info)
    }
}

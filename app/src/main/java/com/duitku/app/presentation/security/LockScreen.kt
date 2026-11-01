package com.duitku.app.presentation.security

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.duitku.app.domain.usecase.security.PinHasher
import com.duitku.app.presentation.theme.DuitKuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Layar kunci: PIN 4-6 digit dan sidik jari/wajah bila diaktifkan. Menutupi seluruh aplikasi. */
@Composable
fun LockScreen(
    lock: AppLockManager,
    biometricEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableLongStateOf(lock.remainingLockMillis()) }
    val canBiometric = biometricEnabled && remember { BiometricHelper.isAvailable(context) }

    fun promptBiometric() {
        val activity = context.findActivity() ?: return
        BiometricHelper.prompt(activity, "Buka DuitKu", "Pindai sidik jari atau wajah", onSuccess = { lock.unlock() })
    }

    // Hitung mundur jeda percobaan.
    LaunchedEffect(remaining > 0) {
        while (remaining > 0) {
            delay(500)
            remaining = lock.remainingLockMillis()
        }
    }
    LaunchedEffect(canBiometric) { if (canBiometric) promptBiometric() }

    fun submit() {
        if (pin.length < PinHasher.MIN_LENGTH) return
        scope.launch {
            when (val r = lock.check(pin)) {
                PinCheck.Success -> Unit
                is PinCheck.Wrong -> {
                    error = if (r.lockedForMillis > 0) "PIN salah. Coba lagi setelah ${r.lockedForMillis / 1000} detik." else "PIN salah"
                    pin = ""
                    remaining = lock.remainingLockMillis()
                }
                is PinCheck.Locked -> {
                    error = "Terlalu banyak percobaan"
                    pin = ""
                    remaining = r.remainingMillis
                }
            }
        }
    }

    LockContent(
        pin = pin, error = error, lockedSeconds = (remaining + 999) / 1000, canBiometric = canBiometric, modifier = modifier,
        onDigit = { d -> if (remaining <= 0 && pin.length < PinHasher.MAX_LENGTH) { pin += d; error = null; if (pin.length == PinHasher.MAX_LENGTH) submit() } },
        onBackspace = { pin = pin.dropLast(1) },
        onSubmit = ::submit,
        onBiometric = ::promptBiometric,
    )
}

@Composable
fun LockContent(
    pin: String,
    error: String?,
    lockedSeconds: Long,
    canBiometric: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("DuitKu terkunci", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text("Masukkan PIN Anda", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(PinHasher.MAX_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                text = when {
                    lockedSeconds > 0 -> "Terkunci sementara. Coba lagi dalam $lockedSeconds detik."
                    else -> error.orEmpty()
                },
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.height(40.dp),
            )

            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { d -> PinKey(label = d, onClick = { onDigit(d) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                if (canBiometric) {
                    PinKey(label = null, description = "Gunakan sidik jari", onClick = onBiometric) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    }
                } else {
                    Box(Modifier.size(72.dp))
                }
                PinKey(label = "0", onClick = { onDigit("0") })
                PinKey(label = null, description = "Hapus digit terakhir", onClick = onBackspace) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
                }
            }
            Button(
                onClick = onSubmit, enabled = pin.length >= PinHasher.MIN_LENGTH && lockedSeconds <= 0,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Buka") }
        }
    }
}

@Composable
private fun PinKey(
    label: String?,
    description: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit = { Text(label.orEmpty(), style = MaterialTheme.typography.headlineMedium) },
) {
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LockPreview() {
    DuitKuTheme(dynamicColor = false) {
        LockContent("12", null, 0, true, {}, {}, {}, {})
    }
}

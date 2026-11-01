package com.duitku.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.StorageUsage
import com.duitku.app.domain.model.ThemeMode
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.TimePickerDialogLocal
import com.duitku.app.presentation.security.BiometricHelper
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalTime
import kotlinx.coroutines.launch

private sealed interface Dialog {
    data object Owner : Dialog
    data object Currency : Dialog
    data object StartDay : Dialog
    data object Theme : Dialog
    data object ReminderTime : Dialog
    data object SetPin : Dialog
    data object RemovePin : Dialog
    data object ResetStep1 : Dialog
    data object ResetStep2 : Dialog
}

private fun size(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRetention: () -> Unit,
    onBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val s = state.settings

    Scaffold(topBar = { DuitKuTopBar(title = "Pengaturan", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Header("Tampilan") }
            item { Row1("Tema", s.themeMode.label) { dialog = Dialog.Theme } }
            item { Switch1("Warna dinamis (Android 12+)", "Ganti identitas DuitKu dengan warna wallpaper", s.dynamicColor, viewModel::setDynamicColor) }

            item { Header("Umum") }
            item { Row1("Nama pemilik", s.ownerName.ifBlank { "Belum diisi (dicetak di PDF)" }) { dialog = Dialog.Owner } }
            item { Row1("Mata uang", "${MoneyFormatter.symbolOf(s.currencyCode)} ${s.currencyCode}") { dialog = Dialog.Currency } }
            item { Row1("Tanggal awal bulan", "Tanggal ${s.monthStartDay}" + if (s.monthStartDay > 1) " (mis. tanggal gajian)" else "") { dialog = Dialog.StartDay } }

            item { Header("Keamanan") }
            item {
                if (s.pinEnabled) {
                    Row1("Ganti PIN", "PIN aktif") { dialog = Dialog.SetPin }
                } else {
                    Row1("Aktifkan PIN", "Kunci aplikasi dengan PIN 4-6 digit") { dialog = Dialog.SetPin }
                }
            }
            if (s.pinEnabled) {
                item { Row1("Nonaktifkan PIN", "Butuh PIN saat ini") { dialog = Dialog.RemovePin } }
                item {
                    val available = remember { BiometricHelper.isAvailable(context) }
                    Switch1(
                        "Sidik jari / wajah", if (available) "Buka aplikasi dengan biometrik" else "Biometrik tidak tersedia di perangkat ini",
                        s.biometricEnabled && available, viewModel::setBiometric, enabled = available,
                    )
                }
            }

            item { Header("Pengingat & notifikasi") }
            item { Switch1("Pengingat harian", "Ingatkan mencatat transaksi pukul %02d.%02d".format(s.reminderHour, s.reminderMinute), s.dailyReminderEnabled, viewModel::setReminder) }
            if (s.dailyReminderEnabled) item { Row1("Jam pengingat", "%02d.%02d".format(s.reminderHour, s.reminderMinute)) { dialog = Dialog.ReminderTime } }
            item { Switch1("Notifikasi anggaran", "Peringatan saat terpakai 80% dan 100%", s.budgetNotificationsEnabled, viewModel::setBudgetNotifications) }
            item { Switch1("Tutup bulan otomatis", "Arsipkan mutasi bulan lalu 3 hari setelah periode berakhir", s.autoCloseMonthEnabled, viewModel::setAutoClose) }

            item { Header("Data") }
            item { Row1("Retensi data", "${AppSettings.retentionLabel(s.retentionMonths)} · ${s.retentionAction.label}", onRetention) }
            item { Row1("Backup & restore", "Auto-backup, file .duitku, pulihkan", onBackup) }
            item { Row1("Isi data contoh 12 bulan", if (s.demoDataLoaded) "Sudah dimuat" else "Untuk mencoba grafik dan laporan") { viewModel.loadDemoData() } }

            item { Header("Penyimpanan") }
            item { StorageSection(state.usage) }

            item { Header("Zona berbahaya") }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = Dialog.ResetStep1 },
                    headlineContent = { Text("Reset semua data", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Menghapus seluruh dompet, transaksi, arsip, dan pengaturan") },
                )
            }
            item { Text("DuitKu 1.0.0 · data tersimpan hanya di perangkat ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp)) }
        }
    }

    when (dialog) {
        Dialog.Theme -> ChoiceDialog("Tema", ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(s.themeMode), { viewModel.setTheme(ThemeMode.entries[it]); dialog = null }) { dialog = null }
        Dialog.Currency -> ChoiceDialog("Mata uang", MoneyFormatter.supportedCodes.map { "${MoneyFormatter.symbolOf(it)}  $it" }, MoneyFormatter.supportedCodes.indexOf(s.currencyCode), { viewModel.setCurrency(MoneyFormatter.supportedCodes[it]); dialog = null }) { dialog = null }
        Dialog.Owner -> TextDialog("Nama pemilik", s.ownerName, { viewModel.setOwner(it); dialog = null }) { dialog = null }
        Dialog.StartDay -> StartDayDialog(s.monthStartDay, { viewModel.setStartDay(it); dialog = null }) { dialog = null }
        Dialog.ReminderTime -> TimePickerDialogLocal(LocalTime.of(s.reminderHour, s.reminderMinute), { viewModel.setReminderTime(it.hour, it.minute); dialog = null }) { dialog = null }
        Dialog.SetPin -> PinDialog(
            requireCurrent = s.pinEnabled,
            onSave = { cur, n, c -> viewModel.savePin(cur, n, c) },
            onDone = { dialog = null },
        )
        Dialog.RemovePin -> PinDialog(requireCurrent = true, removeMode = true, onSave = { cur, _, _ -> viewModel.removePin(cur) }, onDone = { dialog = null })
        Dialog.ResetStep1 -> ResetStep1({ dialog = Dialog.ResetStep2 }) { dialog = null }
        Dialog.ResetStep2 -> ResetStep2(
            pinRequired = s.pinEnabled,
            onConfirm = { word, pin -> viewModel.resetAll(word, pin) },
            onDone = { dialog = null; viewModel.refreshUsage() },
        )
        null -> Unit
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Row1(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(modifier = Modifier.clickable(onClick = onClick), headlineContent = { Text(title) }, supportingContent = { Text(subtitle) })
    HorizontalDivider()
}

@Composable
private fun Switch1(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
    HorizontalDivider()
}

@Composable
private fun StorageSection(usage: StorageUsage?) {
    if (usage == null) {
        Text("Menghitung...", Modifier.padding(16.dp))
        return
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "Database" to size(usage.databaseBytes),
            "${usage.transactionCount} transaksi · ${usage.statementCount} arsip bulanan" to size(usage.snapshotBytes),
            "Hasil ekspor (PDF/CSV/XLSX)" to size(usage.exportBytes),
            "Backup di penyimpanan aplikasi" to size(usage.backupBytes),
            "Total" to size(usage.totalBytes),
        ).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(i) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = i == selected, onClick = { onSelect(i) })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it.take(40) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun StartDayDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(initial.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Tanggal awal bulan") },
        text = {
            Column {
                Text("Tanggal ${day.toInt()}", style = MaterialTheme.typography.titleLarge)
                Slider(value = day, onValueChange = { day = it }, valueRange = 1f..28f, steps = 26)
                Text(
                    "Mengubah ini menggeser batas periode bulanan. Bulan yang sudah ditutup tidak berubah (arsipnya permanen), " +
                        "tetapi periode berikutnya mengikuti tanggal baru.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(day.toInt()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun PinDialog(
    requireCurrent: Boolean,
    onSave: suspend (current: String, new: String, confirm: String) -> String?,
    onDone: () -> Unit,
    removeMode: Boolean = false,
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (removeMode) "Nonaktifkan PIN" else if (requireCurrent) "Ganti PIN" else "Aktifkan PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requireCurrent) {
                    OutlinedTextField(current, { current = it.filter(Char::isDigit).take(6) }, label = { Text("PIN saat ini") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric)
                }
                if (!removeMode) {
                    OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN baru (4-6 digit)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric)
                    OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(6) }, label = { Text("Ulangi PIN baru") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { val e = onSave(current, newPin, confirm); if (e == null) onDone() else error = e } }) { Text(if (removeMode) "Nonaktifkan" else "Simpan") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Batal") } },
    )
}

@Composable
private fun ResetStep1(onNext: () -> Unit, onDismiss: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset semua data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seluruh dompet, transaksi, anggaran, target, utang, arsip bulanan, dan pengaturan akan dihapus. Aplikasi kembali ke kondisi awal.")
                Text("Sebelum menghapus, DuitKu membuat backup pengaman di penyimpanan aplikasi. Jika backup gagal, reset dibatalkan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("Saya mengerti dan ingin melanjutkan", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onNext, enabled = understood) { Text("Lanjut", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ResetStep2(pinRequired: Boolean, onConfirm: suspend (String, String) -> String?, onDone: () -> Unit) {
    var word by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!running) onDone() },
        title = { Text("Konfirmasi terakhir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ketik ${SettingsViewModel.RESET_WORD} untuk menghapus semua data.")
                OutlinedTextField(word, { word = it.take(10) }, singleLine = true, label = { Text("Ketik ${SettingsViewModel.RESET_WORD}") })
                if (pinRequired) {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, singleLine = true, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !running,
                onClick = { running = true; scope.launch { val e = onConfirm(word, pin); running = false; if (e == null) onDone() else error = e } },
            ) { Text(if (running) "Menghapus..." else "Hapus semua", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !running) { Text("Batal") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun StoragePreview() {
    DuitKuTheme(dynamicColor = false) { StorageSection(StorageUsage(2_500_000, 800_000, 1_200_000, 700, 11, 400_000)) }
}

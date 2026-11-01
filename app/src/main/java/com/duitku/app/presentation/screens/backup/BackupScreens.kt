package com.duitku.app.presentation.screens.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.BackupFrequency
import com.duitku.app.domain.model.RestoreMode
import com.duitku.app.domain.model.RetentionAction
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.LoadingBox

private fun sizeText(bytes: Long) = if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${bytes / 1024} KB"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            // Izin baca-tulis dipertahankan agar WorkManager dapat menulis tanpa interaksi pengguna.
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.setFolder(uri.toString())
        }
    }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.backupToUri(it.toString()) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspect(it.toString()) }
    }
    val s = state.settings

    Scaffold(topBar = { DuitKuTopBar(title = "Backup & Restore", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Backup terakhir", style = MaterialTheme.typography.labelLarge)
                    Text(if (s.lastBackupAt > 0) DateFormats.dateTime(s.lastBackupAt) else "Belum pernah", style = MaterialTheme.typography.titleLarge)
                }
            }

            Text("Backup otomatis", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupFrequency.entries.forEach { f -> FilterChip(selected = s.backupFrequency == f, onClick = { viewModel.setFrequency(f) }, label = { Text(f.label) }) }
            }
            Text(
                "Backup tersimpan di folder pilihan Anda. DuitKu menyimpan ${s.keepBackupCount} backup terbaru dan menghapus yang lebih lama.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(if (s.backupFolderUri.isBlank()) "Pilih folder" else "Ganti folder") }
                if (s.backupFolderUri.isNotBlank()) TextButton(onClick = viewModel::clearFolder) { Text("Lepas folder") }
            }
            Text(
                if (s.backupFolderUri.isBlank()) "Folder belum dipilih: backup disimpan di penyimpanan aplikasi (hilang bila aplikasi di-uninstall)." else "Folder: ${Uri.parse(s.backupFolderUri).lastPathSegment}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
            )

            Text("Backup manual", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !state.busy,
                    onClick = { if (s.backupFolderUri.isNotBlank()) viewModel.backupNowToFolder() else createLauncher.launch("DuitKu_backup_${DateFormats.fileStamp()}.duitku") },
                ) { Text(if (state.busy) "Memproses..." else "Cadangkan sekarang") }
                OutlinedButton(enabled = !state.busy, onClick = { createLauncher.launch("DuitKu_backup_${DateFormats.fileStamp()}.duitku") }) { Text("Simpan ke...") }
            }

            Text("Pulihkan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pilih berkas .duitku. Versi dan checksum diperiksa lebih dulu; kondisi saat ini otomatis dicadangkan sebelum dipulihkan.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(enabled = !state.busy, onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Pilih berkas backup") }

            Text("Riwayat backup", style = MaterialTheme.typography.titleMedium)
            if (state.records.isEmpty()) Text("Belum ada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.records.take(15).forEach { r ->
                Column {
                    Text("${r.kind.label} · ${DateFormats.dateTime(r.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${r.fileName} · ${sizeText(r.sizeBytes)} · ${r.transactionCount} transaksi" + if (r.message.isNotBlank()) "\n${r.message}" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    state.pending?.let { p ->
        var mode by remember { mutableStateOf(RestoreMode.MERGE) }
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Pulihkan dari backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dibuat ${DateFormats.dateTime(p.info.createdAt)} · aplikasi ${p.info.appVersion} (database v${p.info.dbVersion})\n" +
                            "${p.info.wallets} dompet · ${p.info.transactions} transaksi · ${p.info.statements} arsip bulanan\n" +
                            (if (p.info.oldestTransactionAt != null) "Rentang ${DateFormats.date(p.info.oldestTransactionAt)} - ${DateFormats.date(p.info.newestTransactionAt ?: p.info.oldestTransactionAt)}\n" else "") +
                            "Ukuran ${sizeText(p.info.sizeBytes)} · checksum valid",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RestoreMode.entries.forEach { m ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = mode == m, onClick = { mode = m })
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (mode == RestoreMode.OVERWRITE) {
                        Text("Semua data saat ini akan diganti dengan isi backup.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.restore(mode) }) { Text("Pulihkan") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Batal") } },
        )
    }
    state.resultMessage?.let {
        ConfirmDialog("Hasil", it, "OK", viewModel::dismissResult, viewModel::dismissResult, dismissLabel = "Tutup")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetentionScreen(onBack: () -> Unit, viewModel: RetentionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmPurge by remember { mutableStateOf(false) }

    Scaffold(topBar = { DuitKuTopBar(title = "Retensi Data", onBack = onBack) }) { padding ->
        val status = state.status
        if (state.isLoading || status == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Lama penyimpanan detail transaksi", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.RETENTION_OPTIONS.forEach { m ->
                    FilterChip(selected = state.retentionMonths == m, onClick = { viewModel.setMonths(m) }, label = { Text(AppSettings.retentionLabel(m)) })
                }
            }
            Text(
                "Data dalam ${AppSettings.retentionLabel(state.retentionMonths).lowercase()} terakhir (ditambah bulan berjalan) tidak akan disentuh. " +
                    "Snapshot mutasi bulanan dan PDF yang sudah diekspor selalu disimpan; hanya detail transaksi mentah yang boleh dibersihkan.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Saat data melewati masa retensi", style = MaterialTheme.typography.titleMedium)
            RetentionAction.entries.forEach { a ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = state.action == a, onClick = { viewModel.setAction(a) })
                    Text(a.label)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Text("Data tertua: " + (status.oldestTransactionAt?.let { DateFormats.date(it) } ?: "-"))
                    when {
                        state.retentionMonths == AppSettings.RETENTION_FOREVER -> Text("Retensi Selamanya: tidak ada data yang dibersihkan.")
                        !status.hasExpiredData -> Text(if (status.expiresSoon) "Sebagian data akan melewati masa retensi dalam 30 hari. Anda akan diberi tahu." else "Tidak ada data yang melewati masa retensi.")
                        else -> {
                            Text("${status.expiredTransactionCount} transaksi (${status.expiredPeriods.size} bulan) melewati masa retensi.", color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                if (status.prepared) "Arsip dan backup sudah disiapkan ${DateFormats.dateTime(status.preparedAt)}."
                                else "Belum diarsipkan. Siapkan arsip lebih dulu sebelum membersihkan.",
                            )
                        }
                    }
                }
            }

            state.progress?.let { (p, label) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$label ($p%)")
                        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (status.hasExpiredData && state.retentionMonths != AppSettings.RETENTION_FOREVER) {
                Button(onClick = viewModel::prepare, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (status.prepared) "Siapkan ulang arsip" else "Siapkan arsip & backup")
                }
                if (state.action == RetentionAction.ASK_BEFORE_PURGE) {
                    OutlinedButton(onClick = { confirmPurge = true }, enabled = status.canPurge && state.progress == null, modifier = Modifier.fillMaxWidth()) {
                        Text("Bersihkan detail transaksi lama", color = if (status.canPurge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Text("Mode \"Pindahkan ke arsip saja\": data tidak pernah dihapus.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            state.message?.let {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        AssistChip(onClick = viewModel::dismissMessage, label = { Text("OK") })
                    }
                }
            }
        }
    }
    if (confirmPurge) {
        ConfirmDialog(
            title = "Bersihkan data lama?",
            message = "Detail ${state.status?.expiredTransactionCount ?: 0} transaksi lama akan dihapus dari perangkat. Saldo dompet tidak berubah, snapshot bulanan " +
                "dan PDF laporan tahunan tetap ada, dan backup penuh sudah dibuat. Tindakan ini tidak dapat diurungkan dari aplikasi.",
            confirmLabel = "Bersihkan", destructive = true,
            onConfirm = { confirmPurge = false; viewModel.purge() }, onDismiss = { confirmPurge = false },
        )
    }
}

package com.duitku.app.presentation.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.DefaultCategories
import com.duitku.app.domain.model.DefaultCategoryGroup
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.YearMonth

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Finished -> onFinished()
            }
        }
    }
    OnboardingContent(
        state = state,
        onOwnerNameChange = viewModel::onOwnerNameChange,
        onWalletNameChange = viewModel::onWalletNameChange,
        onWalletTypeChange = viewModel::onWalletTypeChange,
        onBalanceChange = viewModel::onBalanceChange,
        onToggleCategory = viewModel::toggleCategory,
        onSetAllCategories = viewModel::setAllCategories,
        onStartDayChange = viewModel::onStartDayChange,
        onRetentionChange = viewModel::onRetentionChange,
        onDemoChange = viewModel::onDemoChange,
        onBack = viewModel::back,
        onNext = viewModel::next,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onOwnerNameChange: (String) -> Unit,
    onWalletNameChange: (String) -> Unit,
    onWalletTypeChange: (WalletType) -> Unit,
    onBalanceChange: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onSetAllCategories: (Boolean) -> Unit,
    onStartDayChange: (Int) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onDemoChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = (state.step + 1f) / OnboardingUiState.STEP_COUNT,
        label = "onboarding-progress",
    )
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                label = "onboarding-step",
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> WelcomeStep(state, onOwnerNameChange)
                        1 -> WalletStep(state, onWalletNameChange, onWalletTypeChange, onBalanceChange)
                        2 -> CategoryStep(state, onToggleCategory, onSetAllCategories)
                        else -> SettingsStep(state, onStartDayChange, onRetentionChange, onDemoChange)
                    }
                }
            }

            state.saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step > 0) {
                    OutlinedButton(onClick = onBack, enabled = !state.isSaving, modifier = Modifier.weight(1f)) { Text("Kembali") }
                }
                Button(onClick = onNext, enabled = !state.isSaving, modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            state.isSaving -> "Menyiapkan..."
                            state.isLastStep -> "Selesai"
                            else -> "Lanjut"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(state: OnboardingUiState, onOwnerNameChange: (String) -> Unit) {
    Text("Selamat datang di DuitKu", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Pencatat keuangan pribadi yang bekerja sepenuhnya offline. Data Anda tersimpan di perangkat ini dan tidak dikirim ke mana pun.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.ownerName,
        onValueChange = onOwnerNameChange,
        label = { Text("Nama Anda (untuk laporan PDF)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Langkah berikutnya: membuat dompet pertama, memilih kategori, dan mengatur awal bulan serta masa penyimpanan data.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletStep(
    state: OnboardingUiState,
    onWalletNameChange: (String) -> Unit,
    onWalletTypeChange: (WalletType) -> Unit,
    onBalanceChange: (String) -> Unit,
) {
    Text("Dompet pertama", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Dompet adalah tempat uang Anda berada: tunai, rekening bank, e-wallet, dan sebagainya. Anda bisa menambah dompet lain kapan saja.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.walletName,
        onValueChange = onWalletNameChange,
        label = { Text("Nama dompet") },
        singleLine = true,
        isError = state.walletNameError != null,
        supportingText = state.walletNameError?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WalletType.entries.forEach { type ->
            FilterChip(selected = state.walletType == type, onClick = { onWalletTypeChange(type) }, label = { Text(type.label) })
        }
    }
    OutlinedTextField(
        value = state.balanceInput,
        onValueChange = onBalanceChange,
        label = { Text("Saldo awal") },
        prefix = { Text("Rp ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("Jumlah uang di dompet ini saat ini. Kosongkan bila 0.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryStep(
    state: OnboardingUiState,
    onToggle: (String) -> Unit,
    onSetAll: (Boolean) -> Unit,
) {
    Text("Pilih kategori", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Centang kategori yang ingin dipakai. Semuanya bisa diubah atau ditambah nanti.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onSetAll(true) }) { Text("Pilih semua") }
        TextButton(onClick = { onSetAll(false) }) { Text("Kosongkan") }
    }
    state.categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("Pengeluaran", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    DefaultCategories.expense.forEach { CategoryCheckRow(it, state, onToggle) }
    Text("Pemasukan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    DefaultCategories.income.forEach { CategoryCheckRow(it, state, onToggle) }
}

@Composable
private fun CategoryCheckRow(group: DefaultCategoryGroup, state: OnboardingUiState, onToggle: (String) -> Unit) {
    val key = SeedDefaultCategoriesUseCase.key(group)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = key in state.selectedCategoryKeys, onCheckedChange = { onToggle(key) })
        IconBadge(group.iconKey, group.colorArgb, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.bodyLarge)
            if (group.subcategories.isNotEmpty()) {
                Text(
                    group.subcategories.joinToString(", ") { it.first },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsStep(
    state: OnboardingUiState,
    onStartDayChange: (Int) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onDemoChange: (Boolean) -> Unit,
) {
    Text("Awal bulan & penyimpanan", style = MaterialTheme.typography.headlineMedium)

    Text("Tanggal awal bulan", style = MaterialTheme.typography.titleMedium)
    Text(
        if (state.monthStartDay == 1) "Setiap bulan dihitung dari tanggal 1."
        else "Setiap bulan dihitung dari tanggal ${state.monthStartDay} (misalnya tanggal gajian).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = state.monthStartDay.toFloat(),
        onValueChange = { onStartDayChange(it.toInt()) },
        valueRange = AppSettings.MIN_START_DAY.toFloat()..AppSettings.MAX_START_DAY.toFloat(),
        steps = AppSettings.MAX_START_DAY - AppSettings.MIN_START_DAY - 1,
    )
    val example = PeriodCalculator.rangeOf(YearMonth.now(), state.monthStartDay)
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Text(
            "Contoh periode ${DateFormats.monthYear(example.month)}: ${DateFormats.date(example.start)} s.d. ${DateFormats.date(example.endInclusive)}",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Text("Lama penyimpanan data transaksi", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettings.RETENTION_OPTIONS.forEach { months ->
            FilterChip(
                selected = state.retentionMonths == months,
                onClick = { onRetentionChange(months) },
                label = { Text(AppSettings.retentionLabel(months)) },
            )
        }
    }
    Text(
        "Data tidak pernah dihapus otomatis tanpa izin Anda. Sebelum ada pembersihan, DuitKu membuat backup dan laporan PDF lebih dulu.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Isi data contoh 12 bulan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Untuk mencoba grafik dan laporan. Dompet contoh bisa dihapus kapan saja.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.loadDemoData, onCheckedChange = onDemoChange)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun OnboardingWalletPreview() {
    DuitKuTheme(dynamicColor = false) {
        OnboardingContent(
            state = OnboardingUiState(step = 1),
            onOwnerNameChange = {}, onWalletNameChange = {}, onWalletTypeChange = {}, onBalanceChange = {},
            onToggleCategory = {}, onSetAllCategories = {}, onStartDayChange = {}, onRetentionChange = {},
            onDemoChange = {}, onBack = {}, onNext = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun OnboardingSettingsPreview() {
    DuitKuTheme(dynamicColor = false) {
        OnboardingContent(
            state = OnboardingUiState(step = 3, monthStartDay = 25),
            onOwnerNameChange = {}, onWalletNameChange = {}, onWalletTypeChange = {}, onBalanceChange = {},
            onToggleCategory = {}, onSetAllCategories = {}, onStartDayChange = {}, onRetentionChange = {},
            onDemoChange = {}, onBack = {}, onNext = {},
        )
    }
}

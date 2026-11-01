package com.duitku.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duitku.app.domain.model.BudgetStatus
import com.duitku.app.presentation.theme.DuitKuTheme

/** Warna progres anggaran: hijau (aman), kuning (waspada, >= 80%), merah (melebihi, >= 100%). */
@Composable
fun budgetColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.SAFE -> DuitKuTheme.extended.safe
    BudgetStatus.WARNING -> DuitKuTheme.extended.warning
    BudgetStatus.EXCEEDED -> DuitKuTheme.extended.expense
}

fun statusFor(percent: Int): BudgetStatus = when {
    percent >= 100 -> BudgetStatus.EXCEEDED
    percent >= 80 -> BudgetStatus.WARNING
    else -> BudgetStatus.SAFE
}

@Composable
fun BudgetProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState((percent / 100f).coerceIn(0f, 1f), tween(500), label = "budget-progress")
    LinearProgressIndicator(
        progress = { animated },
        color = budgetColor(statusFor(percent)),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small),
    )
}

@Preview(showBackground = true)
@Composable
private fun BudgetProgressPreview() {
    DuitKuTheme(dynamicColor = false) {
        androidx.compose.foundation.layout.Column {
            BudgetProgressBar(40)
            BudgetProgressBar(85)
            BudgetProgressBar(120)
        }
    }
}

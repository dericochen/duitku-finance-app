package com.duitku.app.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duitku.app.presentation.theme.DuitKuTheme

/** Teks input nominal: hanya digit, tanpa nol di depan, maksimal 12 digit. */
object AmountInput {
    const val MAX_DIGITS = 12

    fun append(current: String, key: String): String {
        val combined = (current + key).trimStart('0')
        return if (combined.length > MAX_DIGITS) current else combined
    }

    fun backspace(current: String): String = current.dropLast(1)

    /** Mengubah nilai Long menjadi teks input (0 → kosong). */
    fun fromLong(value: Long): String = if (value > 0) value.toString() else ""
}

/** Keypad angka ramah satu tangan: 1-9, 000, 0, hapus (tekan lama = kosongkan). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key -> KeypadButton(label = key, onClick = { onKey(key) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeypadButton(label = "000", onClick = { onKey("000") })
            KeypadButton(label = "0", onClick = { onKey("0") })
            KeypadButton(
                onClick = onBackspace,
                onLongClick = onClear,
                description = "Hapus digit terakhir",
                accent = true,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.KeypadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    description: String? = null,
    label: String? = null,
    accent: Boolean = false,
    content: @Composable () -> Unit = {
        Text(
            label.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    },
) {
    val bg = if (accent) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun AmountKeypadPreview() {
    DuitKuTheme(dynamicColor = false) { AmountKeypad(onKey = {}, onBackspace = {}, onClear = {}) }
}

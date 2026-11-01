package com.duitku.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duitku.app.domain.model.Palette
import com.duitku.app.presentation.theme.DuitKuTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selectedArgb: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Int> = Palette.colors,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { argb ->
            val selected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (selected) {
                            Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelected(argb) }
                    .semantics { contentDescription = if (selected) "Warna terpilih" else "Pilih warna" },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selectedKey: String,
    colorArgb: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    keys: List<String> = IconCatalog.categoryKeys,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(BorderStroke(2.dp, Color(colorArgb)), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelected(key) }
                    .padding(3.dp),
            ) {
                IconBadge(
                    iconKey = key,
                    colorArgb = if (selected) colorArgb else MaterialTheme.colorScheme.outline.toArgb(),
                    size = 44.dp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PickersPreview() {
    DuitKuTheme(dynamicColor = false) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            ColorPicker(selectedArgb = Palette.colors[2], onSelected = {})
            IconPicker(selectedKey = "food", colorArgb = Palette.colors[8], onSelected = {})
        }
    }
}

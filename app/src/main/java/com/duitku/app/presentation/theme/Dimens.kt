package com.duitku.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * DUITKU — design tokens.
 * Centralised spacing, radii and elevation so every screen shares one visual rhythm.
 * Prefer these over ad-hoc dp values in composables.
 */
object Dimens {
    // Spacing scale (4dp base)
    val spaceXxs = 2.dp
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val space = 16.dp
    val spaceLg = 20.dp
    val spaceXl = 24.dp
    val spaceXxl = 32.dp

    // Screen edge padding
    val screenPadding = 16.dp

    // Component radii (selective rounding per the design direction)
    val radiusBalanceCard = 26.dp   // hero / balance card: 24-28
    val radiusCard = 18.dp          // standard content cards
    val radiusButton = 16.dp        // buttons: 16-18
    val radiusInput = 14.dp         // input fields: 14-16
    val radiusIconBox = 14.dp       // small icon containers: 14-16
    val radiusChip = 12.dp

    // Shapes
    val balanceCardShape = RoundedCornerShape(radiusBalanceCard)
    val cardShape = RoundedCornerShape(radiusCard)
    val buttonShape = RoundedCornerShape(radiusButton)
    val inputShape = RoundedCornerShape(radiusInput)
    val iconBoxShape = RoundedCornerShape(radiusIconBox)

    // Elevation — restrained shadows
    val cardElevation = 0.dp
    val raisedElevation = 2.dp

    // Touch target
    val minTouchTarget = 48.dp
    val iconBadge = 44.dp
    val keypadKeyHeight = 58.dp
}

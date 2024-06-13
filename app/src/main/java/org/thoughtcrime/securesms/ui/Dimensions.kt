package org.thoughtcrime.securesms.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

data class Dimensions(
    val itemSpacingTiny: Dp = 4.dp,
    val itemSpacingExtraSmall: Dp = 8.dp,
    val itemSpacingSmall: Dp = 16.dp,
    val itemSpacingMedium: Dp = 24.dp,
    val marginTiny: Dp = 8.dp,
    val marginExtraExtraSmall: Dp = 12.dp,
    val marginExtraSmall: Dp = 16.dp,
    val marginSmall: Dp = 24.dp,
    val marginMedium: Dp = 32.dp,
    val marginLarge: Dp = 64.dp,
    val dividerIndent: Dp = 80.dp,
)
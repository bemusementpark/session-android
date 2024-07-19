package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * This class holds two instances of [ThemeColors], [light] representing the [ThemeColors] to use when the system is in a
 * light theme, and [dark] representing the [ThemeColors] to use when the system is in a dark theme.
 */
data class ThemeColorSet(
    val light: ThemeColors,
    val dark: ThemeColors
) {
    fun copy(followSystemSettings: Boolean, isLight: Boolean) = takeIf { followSystemSettings }
        ?: ThemeColorSet(
            light = if (isLight) light else dark,
            dark = if (isLight) light else dark,
        )

    fun copy(primary: Color) = ThemeColorSet(
        light = light.withPrimary(primary),
        dark = dark.withPrimary(primary)
    )

    @Composable
    fun theme(): ThemeColors = if (dark == light || isSystemInDarkTheme()) dark else light
}

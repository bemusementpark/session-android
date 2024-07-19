package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * This class holds two instances of [Colors], [light] representing the [Colors] to use when the system is in a
 * light theme, and [dark] representing the [Colors] to use when the system is in a dark theme.
 */
data class ColorSet(
    val light: Colors,
    val dark: Colors
) {
    fun copy(followSystemSettings: Boolean, isLight: Boolean) = takeIf { followSystemSettings }
        ?: ColorSet(
            light = if (isLight) light else dark,
            dark = if (isLight) light else dark,
        )

    fun copy(primary: Color) = ColorSet(
        light = light.withPrimary(primary),
        dark = dark.withPrimary(primary)
    )

    @Composable
    fun theme(): Colors = if (dark == light || isSystemInDarkTheme()) dark else light
}

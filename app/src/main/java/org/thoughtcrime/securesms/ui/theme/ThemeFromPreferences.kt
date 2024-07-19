package org.thoughtcrime.securesms.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.BLUE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC
import org.session.libsession.utilities.TextSecurePreferences.Companion.LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN
import org.session.libsession.utilities.TextSecurePreferences.Companion.ORANGE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PINK_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PURPLE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.RED_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.YELLOW_ACCENT

val DefaultClassicSet = ThemeColorSet(
    light = ClassicLight(),
    dark = ClassicDark()
)

val DefaultOceanSet = ThemeColorSet(
    light = OceanLight(),
    dark = OceanDark()
)

val colorSetMap = buildMap {
    this[CLASSIC] = DefaultClassicSet
    this[OCEAN] = DefaultOceanSet
}

/**
 * Returns the compose theme based on saved preferences
 * Some behaviour is hardcoded to cater for legacy usage of people with themes already set
 * But future themes will be picked and set directly from the "Appearance" screen
 */
@Composable
fun TextSecurePreferences.getComposeTheme(): ThemeColors {
    val (colorSetString, lightOrDark) = getThemeStyle().split(".")

    val colorSet = (colorSetMap[colorSetString] ?: DefaultClassicSet).copy(primary = primaryColor())

    return colorSet.theme(getFollowSystemSettings(), isLight = lightOrDark == LIGHT)
}

fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> primaryGreen
}

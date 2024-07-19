package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.session.libsession.utilities.AppTextSecurePreferences

// Globally accessible composition local objects
val LocalColors = compositionLocalOf<Colors>{ ClassicDark() }
val LocalType = compositionLocalOf { sessionTypography }

var selectedTheme: Colors? = null

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    // set the theme data if it hasn't been done yet
    if(selectedTheme == null) {
        // Some values can be set from the preferences, and if not should fallback to a default value
        val context = LocalContext.current
        val preferences = AppTextSecurePreferences(context)
        selectedTheme = preferences.getComposeTheme()
    }

    SessionMaterialTheme(colors = selectedTheme ?: ClassicDark()) { content() }
}

/**
 * Apply a given [Colors], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    colors: Colors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colors.toColorScheme(),
        typography = sessionTypography.asMaterialTypography(),
        shapes = sessionShapes,
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalType provides sessionTypography,
            LocalContentColor provides colors.text,
            LocalTextSelectionColors provides colors.textSelectionColors,
            content = content
        )
    }
}

val pillShape = RoundedCornerShape(percent = 50)
val buttonShape = pillShape

val sessionShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp)
)

/**
 * Set the Material 2 theme and a background for Compose Previews.
 */
@Composable
fun PreviewTheme(
    colors: Colors = LocalColors.current,
    content: @Composable BoxScope.() -> Unit
) {
    SessionMaterialTheme(colors) {
        Box(modifier = Modifier.background(color = LocalColors.current.background), content = content)
    }
}

// used for previews
class SessionColorsParameterProvider : PreviewParameterProvider<Colors> {
    override val values = sequenceOf(ClassicDark(), ClassicLight(), OceanDark(), OceanLight())
}

package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp

val colorDestructive = Color(0xffFF453A)

const val classicDark0 = 0xff111111
const val classicDark1 = 0xff1B1B1B
const val classicDark2 = 0xff2D2D2D
const val classicDark3 = 0xff414141
const val classicDark4 = 0xff767676
const val classicDark5 = 0xffA1A2A1
const val classicDark6 = 0xffFFFFFF

const val classicLight0 = 0xff000000
const val classicLight1 = 0xff6D6D6D
const val classicLight2 = 0xffA1A2A1
const val classicLight3 = 0xffDFDFDF
const val classicLight4 = 0xffF0F0F0
const val classicLight5 = 0xffF9F9F9
const val classicLight6 = 0xffFFFFFF

const val oceanDark0 = 0xff000000
const val oceanDark1 = 0xff1A1C28
const val oceanDark2 = 0xff252735
const val oceanDark3 = 0xff2B2D40
const val oceanDark4 = 0xff3D4A5D
const val oceanDark5 = 0xffA6A9CE
const val oceanDark6 = 0xff5CAACC
const val oceanDark7 = 0xffFFFFFF

const val oceanLight0 = 0xff000000
const val oceanLight1 = 0xff19345D
const val oceanLight2 = 0xff6A6E90
const val oceanLight3 = 0xff5CAACC
const val oceanLight4 = 0xffB3EDF2
const val oceanLight5 = 0xffE7F3F4
const val oceanLight6 = 0xffECFAFB
const val oceanLight7 = 0xffFCFFFF

val session_accent = Color(0xFF31F196)
val ocean_accent = Color(0xff57C9FA)

val oceanLights = arrayOf(oceanLight0, oceanLight1, oceanLight2, oceanLight3, oceanLight4, oceanLight5, oceanLight6, oceanLight7)
val oceanDarks = arrayOf(oceanDark0, oceanDark1, oceanDark2, oceanDark3, oceanDark4, oceanDark5, oceanDark6, oceanDark7)
val classicLights = arrayOf(classicLight0, classicLight1, classicLight2, classicLight3, classicLight4, classicLight5, classicLight6)
val classicDarks = arrayOf(classicDark0, classicDark1, classicDark2, classicDark3, classicDark4, classicDark5, classicDark6)

val oceanLightColors = oceanLights.map(::Color)
val oceanDarkColors = oceanDarks.map(::Color)
val classicLightColors = classicLights.map(::Color)
val classicDarkColors = classicDarks.map(::Color)

val blackAlpha40 = Color.Black.copy(alpha = 0.4f)

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = colorDestructive)

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        Colors()
    }
}

@Composable
private fun Colors() {
    AppTheme {
        Column {
            Box(Modifier.background(MaterialTheme.colors.primary)) {
                Text("primary")
            }
            Box(Modifier.background(MaterialTheme.colors.primaryVariant)) {
                Text("primaryVariant")
            }
            Box(Modifier.background(MaterialTheme.colors.secondary)) {
                Text("secondary")
            }
            Box(Modifier.background(MaterialTheme.colors.secondaryVariant)) {
                Text("secondaryVariant")
            }
            Box(Modifier.background(MaterialTheme.colors.surface)) {
                Text("surface")
            }
            Box(Modifier.background(MaterialTheme.colors.primarySurface)) {
                Text("primarySurface")
            }
            Box(Modifier.background(MaterialTheme.colors.background)) {
                Text("background")
            }
            Box(Modifier.background(MaterialTheme.colors.error)) {
                Text("error")
            }
        }
    }
}

@Composable
fun outlinedTextFieldColors(
    isError: Boolean
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = if (isError) colorDestructive else LocalContentColor.current.copy(LocalContentAlpha.current),
    focusedBorderColor = Color(classicDark3),
    unfocusedBorderColor = Color(classicDark3),
    placeholderColor = if (isError) colorDestructive else MaterialTheme.colors.onSurface.copy(ContentAlpha.medium)
)

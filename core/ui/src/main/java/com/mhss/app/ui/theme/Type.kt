package com.mhss.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mhss.app.ui.R

// 1. Rubik betűcsalád (Regular és Bold változatokkal)
val Rubik = FontFamily(
    Font(R.font.thyssen),
    Font(R.font.rubik_bold, FontWeight.Bold)
)

// 2. Croissant One betűcsalád
val CroissantOne = FontFamily(
    Font(R.font.croissantone)
)

// 3. Nimbus San L betűcsalád
val NimbusSanL = FontFamily(
    Font(R.font.nimbussanl)
)

// 4. Thyssen betűcsalád
val Thyssen = FontFamily(
    Font(R.font.rubik_regular)
)

// 5. Kurale Regular betűcsalád
val KuraleRegular = FontFamily(
    Font(R.font.kuraleregular)
)

// Dinamikus Typography generáló függvény a megadott betűcsaláddal és méretskálázással
fun getTypography(font: FontFamily, fontSizeScale: Float = 1.0f) = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (16 * fontSizeScale).sp,
        fontFamily = font
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (14 * fontSizeScale).sp,
        fontFamily = font
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (10 * fontSizeScale).sp,
        fontFamily = font
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.W500,
        fontSize = (14 * fontSizeScale).sp,
        fontFamily = font
    ),
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (96 * fontSizeScale).sp,
        fontFamily = font
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (60 * fontSizeScale).sp,
        fontFamily = font
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (48 * fontSizeScale).sp,
        fontFamily = font
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (32 * fontSizeScale).sp,
        fontFamily = font
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (28 * fontSizeScale).sp,
        fontFamily = font
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (24 * fontSizeScale).sp,
        fontFamily = font
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (20 * fontSizeScale).sp,
        fontFamily = font
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (16 * fontSizeScale).sp,
        fontFamily = font
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = (12 * fontSizeScale).sp,
        fontFamily = font
    )
)

package com.movedados.witon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A fonte da marca e a Mulish. Para usa-la:
//   1. baixe os .ttf em fonts.google.com/specimen/Mulish
//   2. salve em res/font/ como mulish_regular.ttf, mulish_semibold.ttf, mulish_bold.ttf
//   3. troque WiTonFontFamily pelo FontFamily(Font(R.font.mulish_regular), ...)
val WiTonFontFamily = FontFamily.Default

val WiTonTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WiTonFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp
    )
)

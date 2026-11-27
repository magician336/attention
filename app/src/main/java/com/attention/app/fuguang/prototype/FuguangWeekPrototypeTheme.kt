package com.attention.app.fuguang.prototype

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FuguangPrototypeColors {
    val paper = Color(0xFFFBF5E6)
    val paper2 = Color(0xFFF5ECD7)
    val card = Color(0xFFFBF5E6)
    val ink = Color(0xFF4F2028)
    val ink2 = Color(0xFF7A4A50)
    val ink3 = Color(0xFFB58A8E)
    val line = Color(0xFFE7DEC4)
    val pink = Color(0xFFFED0D6)
    val hot = Color(0xFFFF82A2)
    val hotDeep = Color(0xFFCC3A62)
    val lime = Color(0xFFE4F6A9)
    val limeDeep = Color(0xFF8FA83C)
    val sage = Color(0xFFBDDE93)
    val sageDeep = Color(0xFF4F6B33)
    val ai = Color(0xFFDBE2C5)
    val me = Color(0xFFFFD4DD)
    val shadow = Color(0x664F2028)
    val white = Color(0xFFFFFFFF)
    val course = Color(0xFFFED0D6)
    val ielts = Color(0xFFBDDE93)
    val prep = Color(0xFFFF82A2)
    val task = Color(0xFFE4F6A9)
    val meeting = Color(0xFFDBE2C5)
    val move = Color(0xFFDBE2C5)
}

@Immutable
data class FuguangPrototypeType(
    val display: TextStyle = style(19.sp, 24.sp, FontWeight.Bold),
    val section: TextStyle = style(19.sp, 25.sp, FontWeight.Bold),
    val title: TextStyle = style(12.5.sp, 19.sp, FontWeight.SemiBold),
    val body: TextStyle = style(12.sp, 20.sp, FontWeight.Normal),
    val bodyStrong: TextStyle = style(12.sp, 19.sp, FontWeight.Bold),
    val label: TextStyle = style(10.5.sp, 16.sp, FontWeight.Medium),
    val caption: TextStyle = style(9.5.sp, 14.sp, FontWeight.Medium),
    val micro: TextStyle = style(8.5.sp, 12.sp, FontWeight.Medium),
    val number: TextStyle = style(27.sp, 30.sp, FontWeight.Bold),
)

val fuguangPrototypeType = FuguangPrototypeType()

val PrototypeScreenHorizontal = 16.dp
val PrototypeCardRadius = 16.dp
val PrototypeSmallRadius = 10.dp

private fun style(size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, weight: FontWeight) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = weight,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

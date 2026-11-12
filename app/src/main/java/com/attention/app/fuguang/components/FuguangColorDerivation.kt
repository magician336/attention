package com.attention.app.fuguang.components

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private data class Oklch(val l: Double, val c: Double, val h: Double)

internal fun deriveRoles(seeds: FuguangSeeds): FuguangRoles {
    val ink = seeds.ink.toOklch()
    val paper = seeds.paper.toOklch()
    val main = seeds.main.toOklch()
    val accent = seeds.accent.toOklch()
    return FuguangRoles(
        ink = seeds.ink,
        ink2 = ink.transform(ink.l * 1.752, ink.c + 0.0193, ink.h + 21.8),
        ink3 = ink.transform(ink.l * 2.021, ink.c + 0.0154, ink.h + 16.6),
        ink4 = ink.transform(ink.l * 2.368, ink.c + 0.0136, ink.h + 11.4),
        inkOnN = ink.transform(ink.l * 1.372, ink.c + 0.0138, ink.h + 16.0),
        paper = seeds.paper,
        line = paper.transform(paper.l * 0.950, paper.c + 0.0097, paper.h + 0.4),
        line2 = paper.transform(paper.l * 0.975, paper.c + 0.0042, paper.h - 0.3),
        n3 = paper.transform(paper.l * 0.985, paper.c + 0.0029, paper.h - 1.4),
        n2 = paper.transform(paper.l * 0.928, paper.c + 0.0114, paper.h - 1.4),
        n1 = paper.transform(paper.l * 0.778, paper.c + 0.0186, paper.h - 4.6),
        main = seeds.main,
        mainDeep = main.transform(main.l * 0.821, main.c + 0.0142, main.h - 0.1),
        mainInk = main.transform(main.l * 0.754, main.c + 0.0022, main.h - 1.5),
        mainSoft = main.transform(main.l + (1 - main.l) * 0.740, main.c * 0.439, main.h),
        mainTint = main.transform(main.l + (1 - main.l) * 0.861, main.c * 0.274, main.h),
        accent = seeds.accent,
        accentDeep = accent.transform(accent.l * 0.56, accent.c - 0.0013, accent.h - 6.7),
        accentDeep2 = accent.transform(accent.l * 0.510, accent.c - 0.0146, accent.h - 8.5),
        accentTint = accent.transform(accent.l + (1 - accent.l) * 0.517, accent.c * 0.416, accent.h + 0.5),
        surface = Color.White,
        danger = Color(0xFF9B4A3E),
    )
}

private fun Oklch.transform(l: Double, c: Double, h: Double): Color =
    Oklch(l.coerceIn(0.0, 1.0), c.coerceAtLeast(0.0), h).toColor()

private fun Color.toOklch(): Oklch {
    val r = red.toLinear()
    val g = green.toLinear()
    val b = blue.toLinear()
    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    val l3 = cbrt(l)
    val m3 = cbrt(m)
    val s3 = cbrt(s)
    val okL = 0.2104542553 * l3 + 0.7936177850 * m3 - 0.0040720468 * s3
    val okA = 1.9779984951 * l3 - 2.4285922050 * m3 + 0.4505937099 * s3
    val okB = 0.0259040371 * l3 + 0.7827717662 * m3 - 0.8086757660 * s3
    val chroma = sqrt(okA * okA + okB * okB)
    var hue = atan2(okB, okA) * 180.0 / PI
    if (hue < 0) hue += 360.0
    return Oklch(okL, chroma, hue)
}

private fun Oklch.toColor(): Color {
    val radians = h * PI / 180.0
    val okA = c * cos(radians)
    val okB = c * sin(radians)
    val l3 = l + 0.3963377774 * okA + 0.2158037573 * okB
    val m3 = l - 0.1055613458 * okA - 0.0638541728 * okB
    val s3 = l - 0.0894841775 * okA - 1.2914855480 * okB
    val lr = l3.pow(3.0)
    val mr = m3.pow(3.0)
    val sr = s3.pow(3.0)
    val red = 4.0767416621 * lr - 3.3077115913 * mr + 0.2309699292 * sr
    val green = -1.2684380046 * lr + 2.6097574011 * mr - 0.3413193965 * sr
    val blue = -0.0041960863 * lr - 0.7034186147 * mr + 1.7076147010 * sr
    return Color(red.toSrgb(), green.toSrgb(), blue.toSrgb(), 1f)
}

private fun Float.toLinear(): Double {
    val value = toDouble()
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

private fun Double.toSrgb(): Float {
    val value = coerceIn(0.0, 1.0)
    return (if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055).toFloat()
}

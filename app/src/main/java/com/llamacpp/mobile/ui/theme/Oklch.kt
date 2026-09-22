package com.llamacpp.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds a Compose [Color] from an Oklch definition, matching the CSS
 * `oklch(L% C H / A)` tokens used by the llama.cpp web UI theme.
 *
 * @param l lightness in 0..1 (CSS percentage divided by 100)
 * @param c chroma
 * @param h hue in degrees
 * @param alpha 0..1
 */
fun oklch(l: Double, c: Double, h: Double, alpha: Double = 1.0): Color {
    val hr = Math.toRadians(h)
    val a = c * cos(hr)
    val b = c * sin(hr)

    val lCube = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val mCube = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val sCube = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)

    val lr = 4.0767416621 * lCube - 3.3077115913 * mCube + 0.2309699292 * sCube
    val lg = -1.2684380046 * lCube + 2.6097574011 * mCube - 0.3413193965 * sCube
    val lb = -0.0041960863 * lCube - 0.7034186147 * mCube + 1.7076147010 * sCube

    return Color(
        red = gamma(lr).toFloat().coerceIn(0f, 1f),
        green = gamma(lg).toFloat().coerceIn(0f, 1f),
        blue = gamma(lb).toFloat().coerceIn(0f, 1f),
        alpha = alpha.toFloat(),
    )
}

/** Oklch with the CSS percentage form, e.g. `oklchPct(98.5, 0.0, 0.0)`. */
fun oklchPct(lPct: Double, c: Double, h: Double, alpha: Double = 1.0): Color =
    oklch(lPct / 100.0, c, h, alpha)

private fun gamma(x: Double): Double =
    if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055

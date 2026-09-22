package com.llamacpp.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens ported 1:1 from the llama.cpp web UI CSS custom properties
// (a shadcn/ui "neutral" theme expressed in oklch).

// ---- Light --------------------------------------------------------------
val LightBackground = oklchPct(100.0, 0.0, 0.0)
val LightForeground = oklchPct(14.5, 0.0, 0.0)
val LightCard = oklchPct(100.0, 0.0, 0.0)
val LightCardForeground = oklchPct(14.5, 0.0, 0.0)
val LightPopover = oklchPct(100.0, 0.0, 0.0)
val LightPopoverForeground = oklchPct(14.5, 0.0, 0.0)
val LightPrimary = oklchPct(20.5, 0.0, 0.0)
val LightPrimaryForeground = oklchPct(98.5, 0.0, 0.0)
val LightSecondary = oklchPct(95.0, 0.0, 0.0)
val LightSecondaryForeground = oklchPct(20.5, 0.0, 0.0)
val LightMuted = oklchPct(97.0, 0.0, 0.0)
val LightMutedForeground = oklchPct(55.6, 0.0, 0.0)
val LightAccent = oklchPct(95.0, 0.0, 0.0)
val LightAccentForeground = oklchPct(20.5, 0.0, 0.0)
val LightDestructive = oklchPct(57.7, 0.245, 27.325)
val LightDestructiveForeground = oklchPct(98.5, 0.0, 0.0)
val LightBorder = oklchPct(87.5, 0.0, 0.0)
val LightInput = oklchPct(92.0, 0.0, 0.0)
val LightRing = oklchPct(55.6, 0.0, 0.0)
val LightSidebar = oklchPct(98.5, 0.0, 0.0)
val LightSidebarForeground = oklchPct(14.5, 0.0, 0.0)
val LightCodeBackground = oklchPct(98.5, 0.0, 0.0)
val LightCodeForeground = oklchPct(14.5, 0.0, 0.0)

// ---- Dark ---------------------------------------------------------------
val DarkBackground = oklchPct(16.0, 0.0, 0.0)
val DarkForeground = oklchPct(98.5, 0.0, 0.0)
val DarkCard = oklchPct(20.5, 0.0, 0.0)
val DarkCardForeground = oklchPct(98.5, 0.0, 0.0)
val DarkPopover = oklchPct(20.5, 0.0, 0.0)
val DarkPopoverForeground = oklchPct(98.5, 0.0, 0.0)
val DarkPrimary = oklchPct(92.2, 0.0, 0.0)
val DarkPrimaryForeground = oklchPct(20.5, 0.0, 0.0)
val DarkSecondary = oklchPct(29.0, 0.0, 0.0)
val DarkSecondaryForeground = oklchPct(98.5, 0.0, 0.0)
val DarkMuted = oklchPct(26.9, 0.0, 0.0)
val DarkMutedForeground = oklchPct(70.8, 0.0, 0.0)
val DarkAccent = oklchPct(26.9, 0.0, 0.0)
val DarkAccentForeground = oklchPct(98.5, 0.0, 0.0)
val DarkDestructive = oklchPct(70.4, 0.191, 22.216)
val DarkDestructiveForeground = oklchPct(98.5, 0.0, 0.0)
val DarkBorder = oklchPct(100.0, 0.0, 0.0, alpha = 0.3)
val DarkInput = oklchPct(100.0, 0.0, 0.0, alpha = 0.3)
val DarkRing = oklchPct(70.8, 0.0, 0.0)
val DarkSidebar = oklchPct(20.0, 0.0, 0.0)
val DarkSidebarForeground = oklchPct(98.5, 0.0, 0.0)
val DarkCodeBackground = oklchPct(22.5, 0.0, 0.0)
val DarkCodeForeground = oklchPct(87.5, 0.0, 0.0)

/**
 * Extra tokens used by the web UI that don't have a direct Material 3 slot
 * (sidebar, code blocks, raw border) — exposed through [LocalLlamaColors].
 */
data class LlamaColors(
    val sidebar: Color,
    val sidebarForeground: Color,
    val border: Color,
    val codeBackground: Color,
    val codeForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
)

internal val LightLlamaColors = LlamaColors(
    sidebar = LightSidebar,
    sidebarForeground = LightSidebarForeground,
    border = LightBorder,
    codeBackground = LightCodeBackground,
    codeForeground = LightCodeForeground,
    muted = LightMuted,
    mutedForeground = LightMutedForeground,
)

internal val DarkLlamaColors = LlamaColors(
    sidebar = DarkSidebar,
    sidebarForeground = DarkSidebarForeground,
    border = DarkBorder,
    codeBackground = DarkCodeBackground,
    codeForeground = DarkCodeForeground,
    muted = DarkMuted,
    mutedForeground = DarkMutedForeground,
)

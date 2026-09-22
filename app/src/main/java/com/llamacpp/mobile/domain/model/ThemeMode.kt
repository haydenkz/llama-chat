package com.llamacpp.mobile.domain.model

enum class ThemeMode(val wire: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun from(wire: String?): ThemeMode =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: System
    }
}

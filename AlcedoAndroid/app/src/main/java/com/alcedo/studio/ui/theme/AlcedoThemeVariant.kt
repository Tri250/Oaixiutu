package com.alcedo.studio.ui.theme

enum class AlcedoThemeVariant(val displayName: String) {
    HASSELBLAD("Hasselblad"),
    GOLD("Gold"),
    WINE("Wine"),
    STEEL("Steel"),
    GRAPHITE("Graphite"),
    MIST("Mist"),
    DYNAMIC("Dynamic");

    companion object {
        fun fromName(name: String): AlcedoThemeVariant {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: HASSELBLAD
        }
    }
}

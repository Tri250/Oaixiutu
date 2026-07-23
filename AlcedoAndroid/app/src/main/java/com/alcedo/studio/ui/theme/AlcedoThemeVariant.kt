package com.alcedo.studio.ui.theme

enum class AlcedoThemeVariant(val displayName: String) {
    PRO_DARK("专业暗色"),
    PIXCAKE("PixCake"),
    DEEP_SPACE("深空黑+青墨绿"),
    HASSELBLAD("Hasselblad"),
    GOLD("Gold"),
    WINE("Wine"),
    STEEL("Steel"),
    GRAPHITE("Graphite"),
    MIST("Mist"),
    DYNAMIC("Dynamic");

    companion object {
        fun fromName(name: String): AlcedoThemeVariant {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: DEEP_SPACE
        }
    }
}

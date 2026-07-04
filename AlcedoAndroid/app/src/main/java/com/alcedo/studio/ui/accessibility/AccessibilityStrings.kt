package com.alcedo.studio.ui.accessibility

import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.StringResources

// Accessibility string provider - uses i18n system
object AccessibilityStrings {

    fun navAlbum(): String = Strings.current.navAlbum
    fun navAiSearch(): String = Strings.current.navAiSearch
    fun navSettings(): String = Strings.current.navSettings

    fun editorUndo(): String = Strings.current.editorUndo
    fun editorRedo(): String = Strings.current.editorRedo
    fun editorCompare(): String = Strings.current.editorCompare
    fun editorSave(): String = Strings.current.editorSave
    fun editorExport(): String = Strings.current.editorExport
    fun editorBack(): String = Strings.current.back

    fun exportBack(): String = Strings.current.back
    fun exportBrowse(): String = Strings.current.browse
    fun exportDownload(): String = Strings.current.download

    fun albumPhoto(): String = Strings.current.navAlbum

    // Fallbacks for items not yet in i18n
    fun adjustExposure(): String = "Adjust exposure"
    fun adjustContrast(): String = "Adjust contrast"
    fun adjustHighlights(): String = "Adjust highlights"
    fun adjustShadows(): String = "Adjust shadows"
    fun adjustSaturation(): String = "Adjust saturation"
    fun adjustVibrance(): String = "Adjust vibrance"
    fun adjustClarity(): String = "Adjust clarity"
    fun adjustSharpen(): String = "Adjust sharpening"
    fun adjustTemperature(): String = "Adjust white balance temperature"
    fun adjustTint(): String = "Adjust white balance tint"

    fun resetAdjustment(): String = "Reset to default"
    fun closePanel(): String = "Close panel"
    fun openPanel(name: String): String = "Open $name panel"

    fun ratingValue(rating: Int): String = "Rating: $rating out of 5"
    fun progressValue(percent: Int): String = "$percent percent complete"
    fun selectedState(selected: Boolean): String = if (selected) "Selected" else "Not selected"
}

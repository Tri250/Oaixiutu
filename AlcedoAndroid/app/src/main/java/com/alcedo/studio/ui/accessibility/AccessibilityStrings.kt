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

    fun adjustExposure(): String = Strings.current.accAdjustExposure
    fun adjustContrast(): String = Strings.current.accAdjustContrast
    fun adjustHighlights(): String = Strings.current.accAdjustHighlights
    fun adjustShadows(): String = Strings.current.accAdjustShadows
    fun adjustSaturation(): String = Strings.current.accAdjustSaturation
    fun adjustVibrance(): String = Strings.current.accAdjustVibrance
    fun adjustClarity(): String = Strings.current.accAdjustClarity
    fun adjustSharpen(): String = Strings.current.accAdjustSharpen
    fun adjustTemperature(): String = Strings.current.accAdjustTemperature
    fun adjustTint(): String = Strings.current.accAdjustTint

    fun resetAdjustment(): String = Strings.current.accResetAdjustment
    fun closePanel(): String = Strings.current.accClosePanel
    fun openPanel(name: String): String = String.format(Strings.current.accOpenPanel, name)

    fun ratingValue(rating: Int): String = String.format(Strings.current.accRatingValue, rating)
    fun progressValue(percent: Int): String = String.format(Strings.current.accProgressValue, percent)
    fun selectedState(selected: Boolean): String = if (selected) Strings.current.accSelected else Strings.current.accNotSelected
}

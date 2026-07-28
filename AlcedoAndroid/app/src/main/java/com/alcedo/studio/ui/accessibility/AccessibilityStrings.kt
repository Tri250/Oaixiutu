package com.alcedo.studio.ui.accessibility

/**
 * Centralised accessibility content-description strings. Kept as constants so
 * the same description is reused for visually identical controls across screens
 * (important for TalkBack consistency) and so they can be localised later via
 * the i18n layer without touching call sites.
 */
object AccessibilityStrings {
    // ---- Album ----
    const val AlbumGrid = "Image grid"
    const val AlbumList = "Image list"
    const val Thumbnail = "Thumbnail"
    const val Selected = "selected"
    const val NotSelected = "not selected"
    const val RatingStars = "Rating"
    const val ImportButton = "Import images"
    const val CreateFolder = "Create folder"
    const val SearchField = "Search images"
    const val ZoomSlider = "Thumbnail size"
    const val CollectionsTree = "Collections folder tree"

    // ---- Editor ----
    const val Viewport = "Image preview"
    const val ZoomIn = "Zoom in"
    const val ZoomOut = "Zoom out"
    const val FitToScreen = "Fit to screen"
    const val Undo = "Undo"
    const val Redo = "Redo"
    const val BeforeAfter = "Compare before and after"
    const val ToneCurve = "Tone curve editor"
    const val ColorWheel = "Color wheel"
    const val CropOverlay = "Crop overlay"
    const val Histogram = "Histogram"
    const val Waveform = "Waveform scope"
    const val Vectorscope = "Vectorscope"
    const val ExportButton = "Export image"
    const val Versions = "Version history"

    // ---- Common ----
    const val Cancel = "Cancel"
    const val Confirm = "Confirm"
    const val Close = "Close"
    const val MoreOptions = "More options"
    const val Loading = "Loading"
    const val Progress = "Progress"

    /**
     * Builds a spoken description for a thumbnail tile, e.g.
     * "Thumbnail, image_001, 3 stars, selected".
     */
    fun thumbnail(name: String, rating: Int, selected: Boolean, isRaw: Boolean): String = buildString {
        append(AccessibilityStrings.Thumbnail)
        append(", ")
        append(name)
        if (isRaw) append(", RAW")
        if (rating > 0) {
            append(", ")
            append(rating)
            append(" star")
            if (rating != 1) append("s")
        }
        append(", ")
        append(if (selected) Selected else NotSelected)
    }
}

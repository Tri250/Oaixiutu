package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * Image metadata extracted from EXIF / XMP / IPTC. Mirrors the desktop
 * ImageMetadata struct used by the inspector and the filter engine.
 */
@Serializable
data class ImageMetadata(
    val imageId: String,
    // Camera
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val lensSerial: String? = null,
    val bodySerial: String? = null,
    // Exposure
    val focalLength: Float? = null,
    val focalLength35mm: Float? = null,
    val aperture: Float? = null,
    val shutterSpeed: String? = null,
    val exposureTime: Double? = null,
    val iso: Int? = null,
    val exposureBias: Float? = null,
    val exposureProgram: String? = null,
    val meteringMode: String? = null,
    // Date / location
    val captureDate: String? = null,
    val captureDateEpoch: Long? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAltitude: Double? = null,
    // Image
    val orientation: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val bitDepth: Int = 8,
    val colorSpace: String? = null,
    val software: String? = null,
    // White balance
    val whiteBalance: String? = null,
    val kelvin: Int? = null,
    // Flash
    val flashFired: Boolean = false,
    val flashMode: String? = null,
    // Authoring
    val artist: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val title: String? = null,
    val keywords: List<String> = emptyList(),
    // RAW specific
    val isRaw: Boolean = false,
    val rawCfaPattern: String? = null,
    val rawBlackLevel: Int = 0,
    val rawWhiteLevel: Int = 16383,
    val rawBps: Int = 14,
) {
    /** A human-readable summary line for the inspector. */
    fun summaryLine(): String = buildString {
        model?.let { append(it) }
        lensModel?.let { if (isNotEmpty()) append(" · "); append(it) }
        focalLength?.let { if (isNotEmpty()) append(" · "); append("${it}mm") }
        aperture?.let { if (isNotEmpty()) append(" · "); append("f/$it") }
        shutterSpeed?.let { if (isNotEmpty()) append(" · "); append(it) }
        iso?.let { if (isNotEmpty()) append(" · "); append("ISO $it") }
    }

    fun dimensionsString(): String = "${width}×${height}"
}

/** Convenience helpers for mapping EXIF rational numbers. */
object ExifRationals {
    fun apertureToFNumber(numerator: Int, denominator: Int): Float? {
        if (denominator == 0) return null
        val valF = numerator.toFloat() / denominator
        if (valF <= 0f) return null
        return kotlin.math.round(valF * 10f) / 10f
    }

    fun toShutterSpeed(exposureTime: Double?): String? {
        if (exposureTime == null || exposureTime <= 0.0) return null
        return if (exposureTime < 1.0) {
            val denom = (1.0 / exposureTime).roundToIntNearest()
            "1/$denom"
        } else {
            "%.1fs".format(exposureTime)
        }
    }

    private fun Double.roundToIntNearest(): Int =
        if (this >= 0) kotlin.math.round(this).toInt() else -kotlin.math.round(-this).toInt()
}

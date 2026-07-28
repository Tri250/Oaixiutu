package com.alcedo.studio.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies a free-text search query into structured filters vs a semantic
 * (CLIP) query. Mirrors core/app/search_query_classifier: a query like
 * "ISO 800 sunset" is split into an EXIF filter (iso=800) and a semantic
 * remainder ("sunset") that drives the CLIP embedding search.
 */
@Singleton
class SearchQueryClassifier @Inject constructor() {

    data class ClassifiedQuery(
        val rawText: String,
        val semanticText: String,
        val exifFilters: Map<String, String>,
        val ratingFilter: IntRange? = null,
        val dateFilter: Pair<Long, Long>? = null,
        val hasSemantic: Boolean,
    ) {
        val hasFilters: Boolean
            get() = exifFilters.isNotEmpty() || ratingFilter != null || dateFilter != null
    }

    /** Parse [query] into a structured classification. */
    fun classify(query: String): ClassifiedQuery {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val exif = mutableMapOf<String, String>()
        val semantic = mutableListOf<String>()
        var rating: IntRange? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower.startsWith("iso") -> {
                    val value = lower.removePrefix("iso").removePrefix("=").removePrefix(":").toIntOrNull()
                    if (value != null) exif["iso"] = value.toString() else semantic.add(token)
                }
                lower.startsWith("f/") || lower.startsWith("f") -> {
                    val v = lower.removePrefix("f").removePrefix("/").toFloatOrNull()
                    if (v != null) exif["aperture"] = v.toString() else semantic.add(token)
                }
                lower.matches(Regex("\\d+mm")) -> exif["focalLength"] = lower.removeSuffix("mm")
                lower.matches(Regex("[1-5]star(s)?")) -> {
                    val r = lower.first().digitToInt()
                    rating = r..r
                }
                lower.matches(Regex("[1-5]-[1-5]")) -> {
                    val parts = lower.split("-")
                    rating = parts[0].toInt()..parts[1].toInt()
                }
                lower.startsWith("camera:") -> exif["cameraModel"] = lower.removePrefix("camera:")
                lower.startsWith("lens:") -> exif["lensModel"] = lower.removePrefix("lens:")
                else -> semantic.add(token)
            }
        }

        val semanticText = semantic.joinToString(" ")
        return ClassifiedQuery(
            rawText = query,
            semanticText = semanticText,
            exifFilters = exif,
            ratingFilter = rating,
            hasSemantic = semanticText.isNotBlank(),
        )
    }

    /** True when [query] contains any structured EXIF/rating token. */
    fun hasStructuredFilters(query: String): Boolean = classify(query).hasFilters
}

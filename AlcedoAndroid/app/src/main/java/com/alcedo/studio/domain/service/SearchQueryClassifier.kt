package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.QueryClassification
import com.alcedo.studio.data.model.QueryType
import java.util.regex.Pattern

/**
 * Search query classifier.
 *
 * Analyzes a raw search query string and classifies it into one of:
 * - EXACT:   Exact name/path match (e.g., "DSC_0001", "2024-06-15")
 * - EXIF:    EXIF metadata search (e.g., "f/1.8", "ISO 100", "Sony", "24mm")
 * - SEMANTIC: Natural language semantic search (e.g., "sunset over mountains")
 * - LABEL:   AI-generated label search (e.g., "portrait", "landscape", "macro")
 */
class SearchQueryClassifier {

    // ── EXIF Patterns ──

    private val exifPatterns = listOf(
        Pattern.compile("f\\s*/?\\s*\\d+(\\.\\d+)?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("iso\\s*\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d+mm"),
        Pattern.compile("\\d+/\\d+s?"),
        Pattern.compile("\\b(Sony|Canon|Nikon|Fujifilm|Leica|Panasonic|Olympus|Hasselblad|Pentax|Ricoh|Sigma|Tamron|Zeiss|Samsung|Apple|Google|Huawei|Xiaomi)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(G\\s*Master|GM|Art|L\\s*Series|Nikkor|Loxia|Batis|Otus|Noct|Summilux|Summicron|Elmarit)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(Full\\s*Frame|APS-C|MFT|Medium\\s*Format|1\\s*inch)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(RAW|JPEG|PNG|TIFF|HEIF|DNG|ARW|CR2|CR3|NEF)\\b", Pattern.CASE_INSENSITIVE),
    )

    private val labelKeywords = setOf(
        "portrait", "landscape", "macro", "close-up", "wide-angle",
        "telephoto", "aerial", "drone", "street", "documentary", "sports",
        "wildlife", "architecture", "interior", "food", "product",
        "fashion", "wedding", "event", "travel", "astro", "night",
        "sunset", "sunrise", "golden hour", "blue hour", "long exposure",
        "bokeh", "silhouette", "reflection", "symmetry", "minimalist",
        "vintage", "monochrome", "black and white", "sepia", "HDR",
        "water", "mountain", "forest", "beach", "cityscape", "urban",
        "rural", "abstract", "nature", "flower", "animal", "bird",
        "insect", "underwater", "snow", "fog", "rain", "storm",
        "splash", "freeze", "panning", "tilt-shift", "fisheye",
        "infrared", "panorama", "timelapse", "selfie", "group"
    )

    private val exactPatterns = listOf(
        Pattern.compile("^(DSC|IMG|PXL|DSCF|_DSC|_MG|SAM|VID)_?\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d{4}[-/]?\\d{2}[-/]?\\d{2}"),
        Pattern.compile("\\.(jpg|jpeg|png|tiff|tif|arw|cr2|cr3|nef|dng|heic|heif|webp|bmp)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\d+$"),
        Pattern.compile("[/\\\\]"),
    )

    // Camera brand patterns for EXIF field detection
    private val cameraBrandPatterns = mapOf(
        "cameraMake" to Pattern.compile("\\b(Sony|Canon|Nikon|Fujifilm|Leica|Panasonic|Olympus|Hasselblad|Pentax|Ricoh|Samsung|Apple|Google|Huawei|Xiaomi)\\b", Pattern.CASE_INSENSITIVE),
        "lens" to Pattern.compile("\\b(G\\s*Master|GM|Art|L\\s*Series|Nikkor|Loxia|Batis|Otus|Zeiss|Sigma|Tamron)\\b", Pattern.CASE_INSENSITIVE),
        "focalLength" to Pattern.compile("\\d{2,4}mm"),
        "aperture" to Pattern.compile("f\\s*/?\\s*\\d+(\\.\\d+)?"),
        "iso" to Pattern.compile("iso\\s*\\d+", Pattern.CASE_INSENSITIVE),
        "date" to Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}"),
    )

    fun classify(rawQuery: String): QueryClassification {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return QueryClassification(
                rawQuery = trimmed,
                classifiedType = QueryType.EXACT,
                isExactName = false,
                isFreeText = false
            )
        }

        val tokens = tokenize(trimmed)
        val lowerQuery = trimmed.lowercase()

        val isExact = matchesAnyPattern(trimmed, exactPatterns)
        val isExif = matchesAnyPattern(trimmed, exifPatterns)
        val labelMatches = labelKeywords.count { lowerQuery.contains(it) }
        val isLabel = tokens.size <= 3 && labelMatches >= maxOf(1, tokens.size / 2)

        // Detect specific EXIF fields
        val exifCameraMake = cameraBrandPatterns["cameraMake"]?.matcher(trimmed)?.find() ?: false
        val exifCameraModel = exifCameraMake
        val exifLensModel = cameraBrandPatterns["lens"]?.matcher(trimmed)?.find() ?: false
        val exifFocalLength = cameraBrandPatterns["focalLength"]?.matcher(trimmed)?.find() ?: false
        val exifAperture = cameraBrandPatterns["aperture"]?.matcher(trimmed)?.find() ?: false
        val exifIso = cameraBrandPatterns["iso"]?.matcher(trimmed)?.find() ?: false
        val exifCaptureDate = cameraBrandPatterns["date"]?.matcher(trimmed)?.find() ?: false

        val classifiedType = when {
            isExact -> QueryType.EXACT
            isExif -> QueryType.EXIF
            isLabel -> QueryType.LABEL
            else -> QueryType.SEMANTIC
        }

        return QueryClassification(
            rawQuery = trimmed,
            classifiedType = classifiedType,
            tokens = tokens,
            confidence = when (classifiedType) {
                QueryType.EXACT -> 0.95f
                QueryType.EXIF -> 0.9f
                QueryType.LABEL -> 0.85f
                QueryType.SEMANTIC -> 0.7f
            },
            isExactName = isExact,
            isFreeText = !isExact && !isExif,
            isExifQuery = isExif,
            isLabelQuery = isLabel,
            isSemanticQuery = !isExact && !isExif && !isLabel,
            exifCameraMake = exifCameraMake,
            exifCameraModel = exifCameraModel,
            exifLensModel = exifLensModel,
            exifFocalLength = exifFocalLength,
            exifAperture = exifAperture,
            exifIso = exifIso,
            exifCaptureDate = exifCaptureDate
        )
    }

    fun searchPriority(queryType: QueryType): List<QueryType> {
        return when (queryType) {
            QueryType.EXACT -> listOf(QueryType.EXACT, QueryType.LABEL, QueryType.EXIF)
            QueryType.EXIF -> listOf(QueryType.EXIF, QueryType.EXACT)
            QueryType.LABEL -> listOf(QueryType.LABEL, QueryType.SEMANTIC)
            QueryType.SEMANTIC -> listOf(QueryType.SEMANTIC, QueryType.LABEL, QueryType.EXACT)
        }
    }

    private fun tokenize(query: String): List<String> {
        return query.split(Regex("[\\s,，]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    private fun matchesAnyPattern(query: String, patterns: List<Pattern>): Boolean {
        return patterns.any { it.matcher(query).find() }
    }
}
package com.alcedo.studio.gpu

/**
 * Shader source management. Provides the SPIR-V shader source identifiers used
 * by the native Vulkan pipeline (vulkan/shaders/.comp files). The shaders are
 * compiled at build time (see CMakeLists VULKAN_SHADERS); this object maps
 * logical stage names to shader asset paths and exposes metadata for the
 * pipeline accelerator and the about/debug screen.
 */
object ShaderSources {

    /** Logical shader name -> compiled SPIR-V asset path (under assets/shaders). */
    val SHADERS: Map<String, String> = mapOf(
        "basic" to "shaders/basic.comp.spv",
        "color" to "shaders/color.comp.spv",
        "cst" to "shaders/cst.comp.spv",
        "detail" to "shaders/detail.comp.spv",
        "film_grain" to "shaders/film_grain.comp.spv",
        "halation" to "shaders/halation.comp.spv",
        "tone_mapping" to "shaders/tone_mapping.comp.spv",
        "edit_pipeline_fused" to "shaders/edit_pipeline_fused.comp.spv",
        "scope_analyzer" to "shaders/scope_analyzer.comp.spv",
        "prng" to "shaders/prng.comp.spv",
        "geometry_utils" to "shaders/geometry_utils.comp.spv",
    )

    /** The fused pipeline shader that runs the whole edit graph in one dispatch. */
    const val FUSED_PIPELINE = "edit_pipeline_fused"

    /** The scope analyzer shader driving histogram/waveform/vectorscope taps. */
    const val SCOPE_ANALYZER = "scope_analyzer"

    /** Shader stage -> display name for the debug/about screen. */
    fun displayName(name: String): String = when (name) {
        "basic" -> "Basic Tone"
        "color" -> "Color (HSL/Vibrance)"
        "cst" -> "Color Space Transform"
        "detail" -> "Detail (Clarity/Sharpen)"
        "film_grain" -> "Film Grain"
        "halation" -> "Halation"
        "tone_mapping" -> "Tone Mapping"
        "edit_pipeline_fused" -> "Fused Edit Pipeline"
        "scope_analyzer" -> "Scope Analyzer"
        "prng" -> "PRNG"
        "geometry_utils" -> "Geometry Utils"
        else -> name
    }

    /** All shader asset paths, for packaging verification. */
    fun allAssetPaths(): List<String> = SHADERS.values.toList()

    /** True when [name] is a known shader. */
    fun isKnown(name: String): Boolean = SHADERS.containsKey(name)

    /** Asset path for a shader name, or null. */
    fun pathFor(name: String): String? = SHADERS[name]
}

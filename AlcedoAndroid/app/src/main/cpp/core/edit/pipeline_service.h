#include "pipeline_service.h"
#include "image/image_buffer.h"
#include "edit/operators/exposure_op.cpp"
#include "edit/operators/contrast_op.cpp"
#include "edit/operators/saturation_op.cpp"
#include "edit/operators/white_balance_op.cpp"
#include "edit/operators/tone_curve_op.cpp"
#include "edit/operators/sharpen_op.cpp"
#include "edit/operators/film_grain_op.cpp"
#include "edit/operators/vibrance_op.cpp"
#include "edit/operators/tint_op.cpp"
#include "edit/operators/clarity_op.cpp"
#include "edit/operators/tone_region_op.cpp"
#include "edit/operators/hsl_op.cpp"
#include "edit/operators/color_wheel_op.cpp"
#include "edit/operators/channel_mixer_op.cpp"
#include "edit/operators/halation_op.cpp"
#include "edit/operators/lut_op.cpp"
#include "edit/operators/highlight_reconstruction_op.cpp"
#include "edit/operators/rcd_demosaic_op.cpp"
#include "edit/operators/lens_correction_op.cpp"
#include "edit/operators/geometry_op.cpp"
#include "edit/color_science.cpp"
#include <cmath>
#include <algorithm>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "AlcedoPipeline"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// PipelineService Implementation
// ============================================================

PipelineService::PipelineService() {
    for (int i = 0; i < 14; ++i) {
        stage_enabled_[i] = true;
    }
    LOGI("PipelineService created");
}

PipelineService::~PipelineService() {
    LOGI("PipelineService destroyed");
}

void PipelineService::set_backend(BufferBackend backend) {
    backend_ = backend;
    LOGI("Pipeline backend set to %d", static_cast<int>(backend));
}

void PipelineService::set_working_color_space(int space) {
    working_color_space_ = space;
    LOGI("Working color space set to %d", space);
}

void PipelineService::enable_stage(PipelineStage stage, bool enable) {
    stage_enabled_[static_cast<int>(stage)] = enable;
}

bool PipelineService::is_stage_enabled(PipelineStage stage) const {
    return stage_enabled_[static_cast<int>(stage)];
}

// ============================================================
// Main pipeline processing
// ============================================================

bool PipelineService::process(float* pixels, int width, int height, int channels,
                               const PipelineParams& params) {
    if (!pixels || width <= 0 || height <= 0) return false;

    int pixel_count = width * height;
    auto stage = [&](PipelineStage s) -> bool {
        return stage_enabled_[static_cast<int>(s)];
    };

    // Stage 1: Exposure (linear scale)
    if (stage(PipelineStage::EXPOSURE) && params.exposure != 0.0f) {
        apply_exposure(pixels, pixel_count, channels, params.exposure);
    }

    // Stage 2: White Balance
    if (stage(PipelineStage::WHITE_BALANCE) &&
        (params.white_balance_temp != 6500.0f || params.white_balance_tint != 0.0f)) {
        apply_white_balance(pixels, pixel_count, channels,
                            params.white_balance_temp, params.white_balance_tint);
    }

    // Stage 3: Contrast (linear)
    if (stage(PipelineStage::TONE) && params.contrast != 0.0f) {
        apply_contrast(pixels, pixel_count, channels, params.contrast);
    }

    // Stage 4: Tone regions (shadows/midtones/highlights)
    if (stage(PipelineStage::TONE) &&
        (params.highlights != 0.0f || params.shadows != 0.0f || params.midtones != 0.0f)) {
        apply_tone_regions(pixels, pixel_count, channels, params);
    }

    // Stage 5: Tone curve
    if (stage(PipelineStage::TONE_CURVE) && params.tone_curve_points >= 2) {
        apply_tone_curve(pixels, pixel_count, channels, params);
    }

    // Stage 6: Sigmoid contrast
    if (params.sigmoid_contrast != 0.0f) {
        color_science::sigmoid_contrast_bulk(pixels, pixel_count, channels,
                                              params.sigmoid_contrast);
    }

    // Stage 7: Color adjustments (saturation, vibrance, tint, color wheels, HSL, channel mixer)
    if (stage(PipelineStage::COLOR)) {
        apply_color(pixels, pixel_count, channels, params);
    }

    // Stage 8: Clarity (local contrast)
    if (stage(PipelineStage::CLARITY) && params.clarity != 0.0f) {
        apply_clarity(pixels, width, height, channels, params);
    }

    // Stage 9: Sharpen
    if (stage(PipelineStage::SHARPEN) && params.sharpen != 0.0f) {
        apply_sharpen(pixels, width, height, channels, params.sharpen);
    }

    // Stage 10: Effects (film grain, halation, LUT)
    if (stage(PipelineStage::EFFECTS)) {
        apply_effects(pixels, width, height, channels, params);
    }

    // Stage 11: Geometry
    if (stage(PipelineStage::GEOMETRY)) {
        apply_geometry(pixels, width, height, channels, params);
    }

    // Stage 12: Display transform
    if (stage(PipelineStage::DISPLAY_TRANSFORM)) {
        apply_display_transform(pixels, width, height, channels,
                                params.display_transform);
    }

    LOGI("Pipeline completed: %dx%d, %d channels", width, height, channels);
    return true;
}

bool PipelineService::process_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                                   float* output_rgb, int output_width, int output_height,
                                   const PipelineParams& params) {
    if (!raw_data || !output_rgb) return false;

    // Step 1: Highlight reconstruction
    if (params.raw_decode_params.highlight_reconstruction && raw_data) {
        std::vector<uint16_t> raw_copy(raw_data, raw_data + raw_width * raw_height);
        HighlightReconstructionOperator::apply(
            raw_copy.data(), raw_width, raw_height,
            params.raw_decode_params.bayer_pattern,
            static_cast<uint16_t>(params.raw_decode_params.white_level * 0.95f),
            params.raw_decode_params.white_level,
            params.raw_decode_params.black_level);
        raw_data = raw_copy.data();
    }

    // Step 2: Demosaic
    if (params.raw_decode_params.demosaic_algorithm == 0) { // RCD
        RCDDemosaicOperator::demosaic_uint16(
            raw_data, raw_width, raw_height,
            params.raw_decode_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            params.raw_decode_params.white_level,
            params.raw_decode_params.black_level);
    }

    // Step 3: Apply remaining pipeline
    return process(output_rgb, output_width, output_height, 3, params);
}

bool PipelineService::decode_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                                  float* output_rgb, int output_width, int output_height,
                                  const RawDecodeParams& raw_params) {
    if (!raw_data || !output_rgb) return false;

    // Highlight reconstruction
    uint16_t clip_threshold = static_cast<uint16_t>(raw_params.white_level * 0.95f);
    if (raw_params.highlight_reconstruction) {
        std::vector<uint16_t> raw_copy(raw_data, raw_data + raw_width * raw_height);
        HighlightReconstructionOperator::apply(
            raw_copy.data(), raw_width, raw_height,
            raw_params.bayer_pattern,
            clip_threshold,
            raw_params.white_level,
            raw_params.black_level);

        RCDDemosaicOperator::demosaic_uint16(
            raw_copy.data(), raw_width, raw_height,
            raw_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            raw_params.white_level,
            raw_params.black_level);
    } else {
        RCDDemosaicOperator::demosaic_uint16(
            raw_data, raw_width, raw_height,
            raw_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            raw_params.white_level,
            raw_params.black_level);
    }

    LOGI("RAW decode completed: %dx%d -> %dx%d", raw_width, raw_height, output_width, output_height);
    return true;
}

void PipelineService::apply_display_transform(float* pixels, int width, int height, int channels,
                                               const DisplayTransform& transform) {
    int pixel_count = width * height;

    if (transform.color_science == 2) { // LINEAR
        // No tone mapping, just apply EOTF
        switch (transform.eotf) {
            case 0: // sRGB
                color_science::srgb_eotf_rgb(&pixels[0], &pixels[1], &pixels[2]);
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::srgb_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2]);
                }
                break;
            case 1: // PQ
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::pq_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2],
                                                transform.peak_luminance);
                }
                break;
            case 2: // HLG
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::hlg_oetf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2]);
                }
                break;
            case 3: // Gamma 2.2
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::gamma_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2], 2.2f);
                }
                break;
            case 4: // Gamma 2.4
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::gamma_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2], 2.4f);
                }
                break;
        }
    } else if (transform.color_science == 0) { // ACES 2.0
        // Convert to ACES AP0, apply RRT, convert to output, apply EOTF
        color_science::convert_color_space_bulk(pixels, pixel_count, channels, 0, 3); // sRGB → AP0
        color_science::aces_rrt_bulk(pixels, pixel_count, channels, channels);
        // Convert to display color space
        if (transform.display_color_space == 1) { // Display P3
            color_science::convert_color_space_bulk(pixels, pixel_count, channels, 3, 1);
        } else if (transform.display_color_space == 2) { // Rec2020
            color_science::convert_color_space_bulk(pixels, pixel_count, channels, 3, 2);
        } else { // sRGB
            color_science::convert_color_space_bulk(pixels, pixel_count, channels, 3, 0);
        }

        // Apply peak luminance
        color_science::apply_peak_luminance_scale_bulk(pixels, pixel_count, channels,
                                                        transform.peak_luminance);

        // Apply EOTF
        switch (transform.eotf) {
            case 0:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::srgb_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2]);
                }
                break;
            case 1:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::pq_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2],
                                                transform.peak_luminance);
                }
                break;
            case 2:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::hlg_oetf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2]);
                }
                break;
            case 3:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::gamma_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2], 2.2f);
                }
                break;
            case 4:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::gamma_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2], 2.4f);
                }
                break;
        }
    } else if (transform.color_science == 1) { // OpenDRT
        color_science::opendrt_tone_map_bulk(pixels, pixel_count, channels, channels);
        color_science::apply_peak_luminance_scale_bulk(pixels, pixel_count, channels,
                                                        transform.peak_luminance);
        switch (transform.eotf) {
            case 0:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::srgb_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2]);
                }
                break;
            case 1:
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    color_science::pq_eotf_rgb(&pixels[idx], &pixels[idx+1], &pixels[idx+2],
                                                transform.peak_luminance);
                }
                break;
            default: break;
        }
    }
}

bool PipelineService::process_stage(PipelineStage stage, float* pixels, int width, int height,
                                     int channels, const PipelineParams& params) {
    int pixel_count = width * height;

    switch (stage) {
        case PipelineStage::EXPOSURE:
            apply_exposure(pixels, pixel_count, channels, params.exposure);
            break;
        case PipelineStage::WHITE_BALANCE:
            apply_white_balance(pixels, pixel_count, channels,
                                params.white_balance_temp, params.white_balance_tint);
            break;
        case PipelineStage::TONE:
            apply_contrast(pixels, pixel_count, channels, params.contrast);
            apply_tone_regions(pixels, pixel_count, channels, params);
            break;
        case PipelineStage::TONE_CURVE:
            apply_tone_curve(pixels, pixel_count, channels, params);
            break;
        case PipelineStage::COLOR:
            apply_color(pixels, pixel_count, channels, params);
            break;
        case PipelineStage::CLARITY:
            apply_clarity(pixels, width, height, channels, params);
            break;
        case PipelineStage::SHARPEN:
            apply_sharpen(pixels, width, height, channels, params.sharpen);
            break;
        case PipelineStage::EFFECTS:
            apply_effects(pixels, width, height, channels, params);
            break;
        case PipelineStage::DISPLAY_TRANSFORM:
            apply_display_transform(pixels, width, height, channels, params.display_transform);
            break;
        default:
            return false;
    }
    return true;
}

std::string PipelineService::get_pipeline_info() const {
    std::ostringstream ss;
    ss << "Alcedo Pipeline v2.0\n";
    ss << "Backend: " << (backend_ == BufferBackend::CPU ? "CPU" : "GPU") << "\n";
    ss << "Working color space: " << working_color_space_ << "\n";
    ss << "Stages: ";
    const char* stage_names[] = {
        "RAW_DECODE", "HL_RECONSTRUCT", "DEMOSAIC", "EXPOSURE", "WB",
        "TONE", "TONE_CURVE", "COLOR", "CLARITY", "SHARPEN",
        "EFFECTS", "GEOMETRY", "DISPLAY", "FINAL"
    };
    for (int i = 0; i < 14; ++i) {
        if (stage_enabled_[i]) {
            ss << stage_names[i] << " ";
        }
    }
    return ss.str();
}

// ============================================================
// Internal helpers
// ============================================================

void PipelineService::apply_exposure(float* pixels, int count, int channels, float exposure) {
    float scale = std::pow(2.0f, exposure);
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        for (int c = 0; c < 3 && c < channels; ++c) {
            pixels[idx + c] *= scale;
        }
    }
}

void PipelineService::apply_contrast(float* pixels, int count, int channels, float contrast) {
    float scale = 1.0f + contrast;
    float offset = -0.5f * scale + 0.5f;
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        for (int c = 0; c < 3 && c < channels; ++c) {
            pixels[idx + c] = pixels[idx + c] * scale + offset;
            pixels[idx + c] = std::max(0.0f, std::min(1.0f, pixels[idx + c]));
        }
    }
}

void PipelineService::apply_white_balance(float* pixels, int count, int channels, float temp, float tint) {
    float temp_scale = temp / 6500.0f;
    float r_mult = std::max(0.5f, std::min(2.0f, temp_scale));
    float b_mult = std::max(0.5f, std::min(2.0f, 2.0f - temp_scale));
    float g_offset = tint * 0.01f;

    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        pixels[idx]     *= r_mult;
        pixels[idx + 1] += g_offset;
        pixels[idx + 2] *= b_mult;
    }
}

void PipelineService::apply_tone_regions(float* pixels, int count, int channels, const PipelineParams& params) {
    ToneRegionOperator::apply_rgb(pixels, count, 1,  // count pixels, height=1 for flat processing
                                   params.shadows, params.midtones, params.highlights,
                                   params.shadow_boundary, params.highlight_boundary);
}

void PipelineService::apply_tone_curve(float* pixels, int count, int channels, const PipelineParams& params) {
    // Interpolate curve
    auto interpolate = [&](float x) -> float {
        const float* xs = params.tone_curve_x;
        const float* ys = params.tone_curve_y;
        int n = params.tone_curve_points;
        if (x <= xs[0]) return ys[0];
        if (x >= xs[n - 1]) return ys[n - 1];
        for (int i = 0; i < n - 1; ++i) {
            if (x >= xs[i] && x <= xs[i + 1]) {
                float t = (x - xs[i]) / (xs[i + 1] - xs[i]);
                return ys[i] + t * (ys[i + 1] - ys[i]);
            }
        }
        return ys[n - 1];
    };

    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        for (int c = 0; c < 3 && c < channels; ++c) {
            pixels[idx + c] = interpolate(pixels[idx + c]);
        }
    }
}

void PipelineService::apply_color(float* pixels, int count, int channels, const PipelineParams& params) {
    // Saturation
    if (params.saturation != 0.0f) {
        float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
        float s = 1.0f + params.saturation;
        for (int i = 0; i < count; ++i) {
            int idx = i * channels;
            float r = pixels[idx];
            float g = pixels[idx + 1];
            float b = pixels[idx + 2];
            float lum = r * lumR + g * lumG + b * lumB;
            pixels[idx]     = lum + (r - lum) * s;
            pixels[idx + 1] = lum + (g - lum) * s;
            pixels[idx + 2] = lum + (b - lum) * s;
        }
    }

    // Vibrance
    if (params.vibrance != 0.0f) {
        VibranceOperator::apply_rgb(pixels, count, 1, params.vibrance);
    }

    // Tint (split toning)
    if (params.tint_highlight_strength > 0.0f || params.tint_shadow_strength > 0.0f) {
        TintOperator::apply_rgb(pixels, count, 1,
                                params.tint_highlight_hue, params.tint_highlight_strength,
                                params.tint_shadow_hue, params.tint_shadow_strength,
                                params.tint_balance);
    }

    // Color wheels (CDL)
    if (params.color_wheel_lift[0] != 0.0f || params.color_wheel_lift[1] != 0.0f ||
        params.color_wheel_lift[2] != 0.0f ||
        params.color_wheel_gamma[0] != 1.0f || params.color_wheel_gamma[1] != 1.0f ||
        params.color_wheel_gamma[2] != 1.0f ||
        params.color_wheel_gain[0] != 1.0f || params.color_wheel_gain[1] != 1.0f ||
        params.color_wheel_gain[2] != 1.0f) {
        ColorWheelOperator::apply_rgb(pixels, count, 1,
                                       params.color_wheel_lift[0],
                                       params.color_wheel_lift[1],
                                       params.color_wheel_lift[2],
                                       params.color_wheel_gamma[0],
                                       params.color_wheel_gamma[1],
                                       params.color_wheel_gamma[2],
                                       params.color_wheel_gain[0],
                                       params.color_wheel_gain[1],
                                       params.color_wheel_gain[2]);
    }

    // HSL
    bool has_hsl = false;
    for (int c = 0; c < 8; ++c) {
        if (params.hsl_hue_shift[c] != 0.0f ||
            params.hsl_saturation_scale[c] != 1.0f ||
            params.hsl_luminance_scale[c] != 1.0f) {
            has_hsl = true;
            break;
        }
    }
    if (has_hsl) {
        HSLOperator::apply_rgb(pixels, count, 1,
                               params.hsl_hue_ranges, params.hsl_hue_width,
                               params.hsl_hue_shift,
                               params.hsl_saturation_scale,
                               params.hsl_luminance_scale);
    }

    // Channel mixer
    bool is_identity = (params.channel_mixer_matrix[0] == 1.0f && params.channel_mixer_matrix[4] == 1.0f &&
                        params.channel_mixer_matrix[8] == 1.0f &&
                        params.channel_mixer_matrix[1] == 0.0f && params.channel_mixer_matrix[2] == 0.0f &&
                        params.channel_mixer_matrix[3] == 0.0f && params.channel_mixer_matrix[5] == 0.0f &&
                        params.channel_mixer_matrix[6] == 0.0f && params.channel_mixer_matrix[7] == 0.0f);
    if (!is_identity) {
        ChannelMixerOperator::apply_rgb(pixels, count, 1,
                                         params.channel_mixer_matrix,
                                         params.channel_mixer_monochrome);
    }
}

void PipelineService::apply_clarity(float* pixels, int width, int height, int channels,
                                     const PipelineParams& params) {
    if (params.clarity != 0.0f) {
        if (channels == 4) {
            ClarityOperator::apply_rgba(pixels, width, height, params.clarity, params.clarity_radius);
        } else {
            ClarityOperator::apply_rgb(pixels, width, height, params.clarity, params.clarity_radius);
        }
    }
}

void PipelineService::apply_sharpen(float* pixels, int width, int height, int channels, float amount) {
    if (amount == 0.0f) return;
    // 3x3 unsharp mask kernel
    std::vector<float> copy(width * height * channels);
    std::copy(pixels, pixels + width * height * channels, copy.begin());

    float kernel[9] = {0.0f, -1.0f, 0.0f, -1.0f, 5.0f, -1.0f, 0.0f, -1.0f, 0.0f};
    float scale = amount * 0.5f;

    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            for (int c = 0; c < 3 && c < channels; ++c) {
                float sum = 0.0f;
                for (int ky = -1; ky <= 1; ++ky) {
                    for (int kx = -1; kx <= 1; ++kx) {
                        int idx = ((y + ky) * width + (x + kx)) * channels + c;
                        sum += copy[idx] * kernel[(ky + 1) * 3 + (kx + 1)];
                    }
                }
                int center = (y * width + x) * channels + c;
                pixels[center] = copy[center] + (sum - copy[center]) * scale;
            }
        }
    }
}

void PipelineService::apply_effects(float* pixels, int width, int height, int channels,
                                     const PipelineParams& params) {
    // Film grain
    if (params.film_grain > 0.0f) {
        FilmGrainOperator::apply(pixels, width, height, params.film_grain);
    }

    // Halation
    if (params.halation_intensity > 0.0f) {
        if (channels == 4) {
            HalationOperator::apply_rgba(pixels, width, height,
                                          params.halation_intensity,
                                          params.halation_threshold,
                                          params.halation_spread,
                                          params.halation_red_bias);
        } else {
            HalationOperator::apply_rgb(pixels, width, height,
                                         params.halation_intensity,
                                         params.halation_threshold,
                                         params.halation_spread,
                                         params.halation_red_bias);
        }
    }

    // LUT
    if (params.lut_enabled && !params.lut_path.empty()) {
        float* lut_data = nullptr;
        int lut_size = 0;
        if (LutOperator::parse_cube_file(params.lut_path, lut_data, lut_size)) {
            if (channels == 4) {
                LutOperator::apply_rgba(pixels, width, height, lut_data, lut_size);
            } else {
                LutOperator::apply_rgb(pixels, width, height, lut_data, lut_size);
            }
            LutOperator::free_parsed_lut(lut_data);
        }
    }
}

void PipelineService::apply_geometry(float* pixels, int width, int height, int channels,
                                      const PipelineParams& params) {
    // Lens correction
    if (params.lens_k1 != 0.0f || params.lens_k2 != 0.0f || params.lens_k3 != 0.0f ||
        params.lens_p1 != 0.0f || params.lens_p2 != 0.0f) {
        if (channels == 4) {
            LensCorrectionOperator::apply_rgba(pixels, width, height,
                                                params.lens_k1, params.lens_k2, params.lens_k3,
                                                params.lens_p1, params.lens_p2,
                                                params.lens_cx, params.lens_cy,
                                                params.lens_focal_ratio);
        } else {
            LensCorrectionOperator::apply_rgb(pixels, width, height,
                                               params.lens_k1, params.lens_k2, params.lens_k3,
                                               params.lens_p1, params.lens_p2,
                                               params.lens_cx, params.lens_cy,
                                               params.lens_focal_ratio);
        }
    }

    // Vignette correction
    if (params.lens_vignette_strength > 0.0f) {
        if (channels == 4) {
            LensCorrectionOperator::correct_vignette_rgba(pixels, width, height,
                                                           params.lens_vignette_strength);
        } else {
            LensCorrectionOperator::correct_vignette_rgb(pixels, width, height,
                                                          params.lens_vignette_strength);
        }
    }
}

} // namespace alcedo
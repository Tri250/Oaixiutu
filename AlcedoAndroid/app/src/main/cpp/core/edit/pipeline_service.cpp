#include "pipeline_service.h"
#include "../image/image_buffer.h"
#include "operators/vibrance_op.h"
#include "operators/tint_op.h"
#include "operators/clarity_op.h"
#include "operators/tone_region_op.h"
#include "operators/hsl_op.h"
#include "operators/color_wheel_op.h"
#include "operators/channel_mixer_op.h"
#include "operators/halation_op.h"
#include "operators/lut_op.h"
#include "operators/highlight_reconstruction_op.h"
#include "operators/rcd_demosaic_op.h"
#include "operators/lens_correction_op.h"
#include "operators/geometry_op.h"
#include "color_science.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <random>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "AlcedoPipeline"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// Helper: FilmGrainOperator (inline to avoid dependency issues)
class FilmGrainOp {
public:
    static void apply(float* pixels, int width, int height, float intensity) {
        std::mt19937 gen(42);
        std::normal_distribution<float> dist(0.0f, intensity * 0.05f);
        int total = width * height * 3;
        for (int i = 0; i < total; ++i) {
            pixels[i] += dist(gen);
            pixels[i] = std::max(0.0f, std::min(1.0f, pixels[i]));
        }
    }
};

// ============================================================
// PipelineService Implementation
// ============================================================

PipelineService::PipelineService() {
    for (int i = 0; i < 14; ++i) stage_enabled_[i] = true;
    LOGI("PipelineService created");
}

PipelineService::~PipelineService() {
    LOGI("PipelineService destroyed");
}

void PipelineService::set_backend(BufferBackend backend) {
    backend_ = backend;
}

void PipelineService::set_working_color_space(int space) {
    working_color_space_ = space;
}

void PipelineService::enable_stage(PipelineStage stage, bool enable) {
    stage_enabled_[static_cast<int>(stage)] = enable;
}

bool PipelineService::is_stage_enabled(PipelineStage stage) const {
    return stage_enabled_[static_cast<int>(stage)];
}

bool PipelineService::process(float* pixels, int width, int height, int channels,
                               const PipelineParams& params) {
    if (!pixels || width <= 0 || height <= 0) return false;
    int pixel_count = width * height;
    auto en = [&](PipelineStage s) { return stage_enabled_[static_cast<int>(s)]; };

    // Stage: Exposure
    if (en(PipelineStage::EXPOSURE) && params.exposure != 0.0f) {
        float scale = std::pow(2.0f, params.exposure);
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) pixels[idx + c] *= scale;
        }
    }

    // Stage: White Balance
    if (en(PipelineStage::WHITE_BALANCE) &&
        (params.white_balance_temp != 6500.0f || params.white_balance_tint != 0.0f)) {
        float temp_scale = params.white_balance_temp / 6500.0f;
        float r_mult = std::max(0.5f, std::min(2.0f, temp_scale));
        float b_mult = std::max(0.5f, std::min(2.0f, 2.0f - temp_scale));
        float g_off = params.white_balance_tint * 0.01f;
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            pixels[idx]     *= r_mult;
            pixels[idx + 1] += g_off;
            pixels[idx + 2] *= b_mult;
        }
    }

    // Stage: Contrast
    if (en(PipelineStage::TONE) && params.contrast != 0.0f) {
        float scale = 1.0f + params.contrast;
        float offset = -0.5f * scale + 0.5f;
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                float& v = pixels[idx + c];
                v = v * scale + offset;
                v = std::max(0.0f, std::min(1.0f, v));
            }
        }
    }

    // Stage: Tone Regions
    if (en(PipelineStage::TONE) &&
        (params.highlights != 0.0f || params.shadows != 0.0f || params.midtones != 0.0f)) {
        ToneRegionOperator::apply_rgb(pixels, width, height,
                                       params.shadows, params.midtones, params.highlights,
                                       params.shadow_boundary, params.highlight_boundary);
    }

    // Stage: Tone Curve
    if (en(PipelineStage::TONE_CURVE) && params.tone_curve_points >= 2) {
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                float x = pixels[idx + c];
                const float* xs = params.tone_curve_x;
                const float* ys = params.tone_curve_y;
                int n = params.tone_curve_points;
                if (x <= xs[0]) { pixels[idx + c] = ys[0]; continue; }
                if (x >= xs[n - 1]) { pixels[idx + c] = ys[n - 1]; continue; }
                for (int j = 0; j < n - 1; ++j) {
                    if (x >= xs[j] && x <= xs[j + 1]) {
                        float t = (x - xs[j]) / (xs[j + 1] - xs[j]);
                        pixels[idx + c] = ys[j] + t * (ys[j + 1] - ys[j]);
                        break;
                    }
                }
            }
        }
    }

    // Stage: Sigmoid contrast
    if (params.sigmoid_contrast != 0.0f) {
        color_science::sigmoid_contrast_bulk(pixels, pixel_count, channels, params.sigmoid_contrast);
    }

    // Stage: Color
    if (en(PipelineStage::COLOR)) {
        // Saturation
        if (params.saturation != 0.0f) {
            float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
            float s = 1.0f + params.saturation;
            for (int i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                float r = pixels[idx], g = pixels[idx + 1], b = pixels[idx + 2];
                float lum = r * lumR + g * lumG + b * lumB;
                pixels[idx]     = lum + (r - lum) * s;
                pixels[idx + 1] = lum + (g - lum) * s;
                pixels[idx + 2] = lum + (b - lum) * s;
            }
        }

        // Vibrance
        if (params.vibrance != 0.0f) {
            VibranceOperator::apply_rgb(pixels, width, height, params.vibrance);
        }

        // Tint
        if (params.tint_highlight_strength > 0.0f || params.tint_shadow_strength > 0.0f) {
            TintOperator::apply_rgb(pixels, width, height,
                                     params.tint_highlight_hue, params.tint_highlight_strength,
                                     params.tint_shadow_hue, params.tint_shadow_strength,
                                     params.tint_balance);
        }

        // Color wheels
        bool cw_nonzero = false;
        for (int c = 0; c < 3; ++c) {
            if (params.color_wheel_lift[c] != 0.0f ||
                params.color_wheel_gamma[c] != 1.0f ||
                params.color_wheel_gain[c] != 1.0f) { cw_nonzero = true; break; }
        }
        if (cw_nonzero) {
            ColorWheelOperator::apply_rgb(pixels, width, height,
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
                params.hsl_luminance_scale[c] != 1.0f) { has_hsl = true; break; }
        }
        if (has_hsl) {
            HSLOperator::apply_rgb(pixels, width, height,
                                   params.hsl_hue_ranges, params.hsl_hue_width,
                                   params.hsl_hue_shift,
                                   params.hsl_saturation_scale,
                                   params.hsl_luminance_scale);
        }

        // Channel mixer
        bool is_identity = true;
        float identity[9] = {1,0,0, 0,1,0, 0,0,1};
        for (int i = 0; i < 9; ++i) {
            if (std::abs(params.channel_mixer_matrix[i] - identity[i]) > 0.0001f) {
                is_identity = false; break;
            }
        }
        if (!is_identity) {
            ChannelMixerOperator::apply_rgb(pixels, width, height,
                                             params.channel_mixer_matrix,
                                             params.channel_mixer_monochrome);
        }
    }

    // Stage: Clarity
    if (en(PipelineStage::CLARITY) && params.clarity != 0.0f) {
        if (channels == 4)
            ClarityOperator::apply_rgba(pixels, width, height, params.clarity, params.clarity_radius);
        else
            ClarityOperator::apply_rgb(pixels, width, height, params.clarity, params.clarity_radius);
    }

    // Stage: Sharpen
    if (en(PipelineStage::SHARPEN) && params.sharpen != 0.0f) {
        std::vector<float> copy(pixels, pixels + width * height * channels);
        float kernel[9] = {0.0f, -1.0f, 0.0f, -1.0f, 5.0f, -1.0f, 0.0f, -1.0f, 0.0f};
        float scale = params.sharpen * 0.5f;
        for (int y = 1; y < height - 1; ++y) {
            for (int x = 1; x < width - 1; ++x) {
                for (int c = 0; c < 3 && c < channels; ++c) {
                    float sum = 0.0f;
                    for (int ky = -1; ky <= 1; ++ky)
                        for (int kx = -1; kx <= 1; ++kx)
                            sum += copy[((y + ky) * width + (x + kx)) * channels + c] *
                                   kernel[(ky + 1) * 3 + (kx + 1)];
                    int center = (y * width + x) * channels + c;
                    pixels[center] = copy[center] + (sum - copy[center]) * scale;
                }
            }
        }
    }

    // Stage: Effects
    if (en(PipelineStage::EFFECTS)) {
        if (params.film_grain > 0.0f) {
            std::mt19937 gen(42);
            std::normal_distribution<float> dist(0.0f, params.film_grain * 0.05f);
            int total = pixel_count * channels;
            for (int i = 0; i < total; ++i) {
                pixels[i] += dist(gen);
                pixels[i] = std::max(0.0f, std::min(1.0f, pixels[i]));
            }
        }

        if (params.halation_intensity > 0.0f) {
            if (channels == 4)
                HalationOperator::apply_rgba(pixels, width, height,
                                              params.halation_intensity, params.halation_threshold,
                                              params.halation_spread, params.halation_red_bias);
            else
                HalationOperator::apply_rgb(pixels, width, height,
                                             params.halation_intensity, params.halation_threshold,
                                             params.halation_spread, params.halation_red_bias);
        }

        if (params.lut_enabled && !params.lut_path.empty()) {
            float* lut_data = nullptr;
            int lut_size = 0;
            if (LutOperator::parse_cube_file(params.lut_path, lut_data, lut_size)) {
                if (channels == 4)
                    LutOperator::apply_rgba(pixels, width, height, lut_data, lut_size);
                else
                    LutOperator::apply_rgb(pixels, width, height, lut_data, lut_size);
                LutOperator::free_parsed_lut(lut_data);
            }
        }
    }

    // Stage: Geometry
    if (en(PipelineStage::GEOMETRY)) {
        if (params.lens_k1 != 0.0f || params.lens_k2 != 0.0f || params.lens_k3 != 0.0f ||
            params.lens_p1 != 0.0f || params.lens_p2 != 0.0f) {
            if (channels == 4)
                LensCorrectionOperator::apply_rgba(pixels, width, height,
                                                    params.lens_k1, params.lens_k2, params.lens_k3,
                                                    params.lens_p1, params.lens_p2,
                                                    params.lens_cx, params.lens_cy,
                                                    params.lens_focal_ratio);
            else
                LensCorrectionOperator::apply_rgb(pixels, width, height,
                                                   params.lens_k1, params.lens_k2, params.lens_k3,
                                                   params.lens_p1, params.lens_p2,
                                                   params.lens_cx, params.lens_cy,
                                                   params.lens_focal_ratio);
        }
        if (params.lens_vignette_strength > 0.0f) {
            if (channels == 4)
                LensCorrectionOperator::correct_vignette_rgba(pixels, width, height,
                                                               params.lens_vignette_strength);
            else
                LensCorrectionOperator::correct_vignette_rgb(pixels, width, height,
                                                              params.lens_vignette_strength);
        }
    }

    // Stage: Display Transform
    if (en(PipelineStage::DISPLAY_TRANSFORM)) {
        apply_display_transform(pixels, width, height, channels, params.display_transform);
    }

    LOGI("Pipeline completed: %dx%d %dch", width, height, channels);
    return true;
}

bool PipelineService::process_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                                   float* output_rgb, int output_width, int output_height,
                                   const PipelineParams& params) {
    if (!raw_data || !output_rgb) return false;

    const uint16_t* src = raw_data;
    std::vector<uint16_t> raw_copy;

    if (params.raw_decode_params.highlight_reconstruction) {
        raw_copy.assign(raw_data, raw_data + raw_width * raw_height);
        HighlightReconstructionOperator::apply(
            raw_copy.data(), raw_width, raw_height,
            params.raw_decode_params.bayer_pattern,
            static_cast<uint16_t>(params.raw_decode_params.white_level * 0.95f),
            params.raw_decode_params.white_level,
            params.raw_decode_params.black_level);
        src = raw_copy.data();
    }

    if (params.raw_decode_params.demosaic_algorithm == 0) {
        RCDDemosaicOperator::demosaic_uint16(
            src, raw_width, raw_height,
            params.raw_decode_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            params.raw_decode_params.white_level,
            params.raw_decode_params.black_level);
    }

    return process(output_rgb, output_width, output_height, 3, params);
}

bool PipelineService::decode_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                                  float* output_rgb, int output_width, int output_height,
                                  const RawDecodeParams& raw_params) {
    if (!raw_data || !output_rgb) return false;

    const uint16_t* src = raw_data;
    std::vector<uint16_t> raw_copy;

    if (raw_params.highlight_reconstruction) {
        raw_copy.assign(raw_data, raw_data + raw_width * raw_height);
        HighlightReconstructionOperator::apply(
            raw_copy.data(), raw_width, raw_height,
            raw_params.bayer_pattern,
            static_cast<uint16_t>(raw_params.white_level * 0.95f),
            raw_params.white_level,
            raw_params.black_level);
        src = raw_copy.data();
    }

    RCDDemosaicOperator::demosaic_uint16(
        src, raw_width, raw_height,
        raw_params.bayer_pattern,
        output_rgb, output_rgb + output_width * output_height,
        output_rgb + 2 * output_width * output_height,
        raw_params.white_level,
        raw_params.black_level);

    LOGI("RAW decode: %dx%d -> %dx%d", raw_width, raw_height, output_width, output_height);
    return true;
}

void PipelineService::apply_display_transform(float* pixels, int width, int height, int channels,
                                               const DisplayTransform& transform) {
    int pixel_count = width * height;

    if (transform.color_science == 2) { // LINEAR
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            float r = pixels[idx], g = pixels[idx+1], b = pixels[idx+2];
            color_science::apply_peak_luminance_scale(&r, &g, &b, transform.peak_luminance);
            switch (transform.eotf) {
                case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
                case 1: color_science::pq_eotf_rgb(&r, &g, &b, transform.peak_luminance); break;
                case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
                case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
                case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
            }
            pixels[idx]=r; pixels[idx+1]=g; pixels[idx+2]=b;
        }
    } else if (transform.color_science == 0) { // ACES 2.0
        // sRGB → AP0 → RRT → display color space → peak luminance → EOTF
        color_science::convert_color_space_bulk(pixels, pixel_count, channels, 0, 3);
        color_science::aces_rrt_bulk(pixels, pixel_count, channels, channels);
        int dst_space = (transform.display_color_space == 1) ? 1 :
                        (transform.display_color_space == 2) ? 2 : 0;
        color_science::convert_color_space_bulk(pixels, pixel_count, channels, 3, dst_space);
        color_science::apply_peak_luminance_scale_bulk(pixels, pixel_count, channels, transform.peak_luminance);
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            float r = pixels[idx], g = pixels[idx+1], b = pixels[idx+2];
            switch (transform.eotf) {
                case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
                case 1: color_science::pq_eotf_rgb(&r, &g, &b, transform.peak_luminance); break;
                case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
                case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
                case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
            }
            pixels[idx]=r; pixels[idx+1]=g; pixels[idx+2]=b;
        }
    } else if (transform.color_science == 1) { // OpenDRT
        color_science::opendrt_tone_map_bulk(pixels, pixel_count, channels, channels);
        color_science::apply_peak_luminance_scale_bulk(pixels, pixel_count, channels, transform.peak_luminance);
        for (int i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            float r = pixels[idx], g = pixels[idx+1], b = pixels[idx+2];
            switch (transform.eotf) {
                case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
                case 1: color_science::pq_eotf_rgb(&r, &g, &b, transform.peak_luminance); break;
                case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
                case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
                case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
            }
            pixels[idx]=r; pixels[idx+1]=g; pixels[idx+2]=b;
        }
    }
}

bool PipelineService::process_stage(PipelineStage stage, float* pixels, int width, int height,
                                     int channels, const PipelineParams& params) {
    int pixel_count = width * height;
    switch (stage) {
        case PipelineStage::EXPOSURE: {
            float scale = std::pow(2.0f, params.exposure);
            for (int i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c) pixels[idx + c] *= scale;
            }
            break;
        }
        case PipelineStage::WHITE_BALANCE: {
            float temp_scale = params.white_balance_temp / 6500.0f;
            float r_mult = std::max(0.5f, std::min(2.0f, temp_scale));
            float b_mult = std::max(0.5f, std::min(2.0f, 2.0f - temp_scale));
            for (int i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                pixels[idx] *= r_mult;
                pixels[idx + 2] *= b_mult;
            }
            break;
        }
        case PipelineStage::TONE: {
            float scale = 1.0f + params.contrast;
            float offset = -0.5f * scale + 0.5f;
            for (int i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c) {
                    float& v = pixels[idx + c];
                    v = v * scale + offset;
                    v = std::max(0.0f, std::min(1.0f, v));
                }
            }
            break;
        }
        case PipelineStage::COLOR: {
            if (params.saturation != 0.0f) {
                float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
                float s = 1.0f + params.saturation;
                for (int i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    float r = pixels[idx], g = pixels[idx + 1], b = pixels[idx + 2];
                    float lum = r * lumR + g * lumG + b * lumB;
                    pixels[idx] = lum + (r - lum) * s;
                    pixels[idx + 1] = lum + (g - lum) * s;
                    pixels[idx + 2] = lum + (b - lum) * s;
                }
            }
            break;
        }
        case PipelineStage::DISPLAY_TRANSFORM:
            apply_display_transform(pixels, width, height, channels, params.display_transform);
            break;
        default: return false;
    }
    return true;
}

std::string PipelineService::get_pipeline_info() const {
    std::ostringstream ss;
    ss << "Alcedo Pipeline v2.0 | Backend: " << (backend_ == BufferBackend::CPU ? "CPU" : "GPU");
    return ss.str();
}

} // namespace alcedo
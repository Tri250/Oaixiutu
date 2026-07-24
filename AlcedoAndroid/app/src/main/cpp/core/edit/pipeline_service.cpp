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
#include "operators/ahd_demosaic_op.h"
#include "operators/amaze_demosaic_op.h"
#include "operators/auto_exposure_op.h"
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

// ============================================================
// NaN/Inf safety utilities (must match color_science.cpp definitions)
// ============================================================

static inline float clamp01_safe(float v) {
    if (!std::isfinite(v)) return 0.0f;
    return std::max(0.0f, std::min(1.0f, v));
}

// ============================================================
// PipelineService Singleton
// ============================================================

std::once_flag PipelineService::init_flag_;
std::unique_ptr<PipelineService> PipelineService::instance_;

PipelineService& PipelineService::Instance() {
    std::call_once(init_flag_, []() {
        instance_ = std::make_unique<PipelineService>();
    });
    return *instance_;
}

// Static random generator for film grain (avoids re-seeding on every call)
static std::mt19937& get_film_grain_gen() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    return gen;
}

// ============================================================
// PipelineService Implementation
// ============================================================

PipelineService::PipelineService() {
    for (int i = 0; i < 16; ++i) stage_enabled_[i] = true;
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
    size_t pixel_count = static_cast<size_t>(width) * height;
    auto en = [&](PipelineStage s) { return stage_enabled_[static_cast<int>(s)]; };

    // Stage: Auto Exposure (must run before Exposure)
    if (en(PipelineStage::AUTO_EXPOSURE) && params.auto_exposure_enabled) {
        PipelineParams mutable_params = params;
        apply_auto_exposure(pixels, width, height, channels, mutable_params);
    }

    // Stage: Exposure
    if (en(PipelineStage::EXPOSURE) && params.exposure != 0.0f) {
        float scale = std::pow(2.0f, params.exposure);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                pixels[idx + c] *= scale;
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
        }
    }

    // Stage: White Balance (Planckian locus for physically accurate color temperature)
    if (en(PipelineStage::WHITE_BALANCE) &&
        (params.white_balance_temp != 6500.0f || params.white_balance_tint != 0.0f)) {
        color_science::planckian_white_balance_bulk(pixels, static_cast<int>(pixel_count), channels,
                                                     params.white_balance_temp,
                                                     params.white_balance_tint);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c)
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
        }
    }

    // Stage: Contrast
    if (en(PipelineStage::TONE) && params.contrast != 0.0f) {
        float scale = 1.0f + params.contrast;
        float offset = -0.5f * scale + 0.5f;
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                float& v = pixels[idx + c];
                v = v * scale + offset;
                v = clamp01_safe(v);
            }
        }
    }

    // Stage: Tone Regions
    if (en(PipelineStage::TONE) &&
        (params.highlights != 0.0f || params.shadows != 0.0f || params.midtones != 0.0f)) {
        ToneRegionOperator::apply_rgb(pixels, width, height,
                                       params.shadows, params.midtones, params.highlights,
                                       params.shadow_boundary, params.highlight_boundary);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c)
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
        }
    }

    // Stage: Tone Curve
    if (en(PipelineStage::TONE_CURVE) && params.tone_curve_points >= 2) {
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                float x = pixels[idx + c];
                const float* xs = params.tone_curve_x;
                const float* ys = params.tone_curve_y;
                int n = params.tone_curve_points;
                if (x <= xs[0]) { pixels[idx + c] = clamp01_safe(ys[0]); continue; }
                if (x >= xs[n - 1]) { pixels[idx + c] = clamp01_safe(ys[n - 1]); continue; }
                for (int j = 0; j < n - 1; ++j) {
                    if (x >= xs[j] && x <= xs[j + 1]) {
                        float denom = xs[j + 1] - xs[j];
                        if (denom <= 0.0f) { pixels[idx + c] = clamp01_safe(ys[j]); break; }
                        float t = (x - xs[j]) / denom;
                        pixels[idx + c] = clamp01_safe(ys[j] + t * (ys[j + 1] - ys[j]));
                        break;
                    }
                }
            }
        }
    }

    // Stage: Sigmoid contrast
    if (params.sigmoid_contrast != 0.0f) {
        color_science::sigmoid_contrast_bulk(pixels, static_cast<int>(pixel_count), channels, params.sigmoid_contrast, params.sigmoid_pivot, params.sigmoid_shoulder);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c)
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
        }
    }

    // Stage: Color
    if (en(PipelineStage::COLOR)) {
        // Saturation
        if (params.saturation != 0.0f) {
            float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
            float s = 1.0f + params.saturation;
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                float r = pixels[idx], g = pixels[idx + 1], b = pixels[idx + 2];
                float lum = r * lumR + g * lumG + b * lumB;
                pixels[idx]     = clamp01_safe(lum + (r - lum) * s);
                pixels[idx + 1] = clamp01_safe(lum + (g - lum) * s);
                pixels[idx + 2] = clamp01_safe(lum + (b - lum) * s);
            }
        }

        // Vibrance
        if (params.vibrance != 0.0f) {
            VibranceOperator::apply_rgb(pixels, width, height, params.vibrance);
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
        }

        // Tint
        if (params.tint_highlight_strength > 0.0f || params.tint_shadow_strength > 0.0f) {
            TintOperator::apply_rgb(pixels, width, height,
                                     params.tint_highlight_hue, params.tint_highlight_strength,
                                     params.tint_shadow_hue, params.tint_shadow_strength,
                                     params.tint_balance);
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
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
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
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
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
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
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
        }
    }

    // Stage: Clarity
    if (en(PipelineStage::CLARITY) && params.clarity != 0.0f) {
        if (channels == 4)
            ClarityOperator::apply_rgba(pixels, width, height, params.clarity, params.clarity_radius);
        else
            ClarityOperator::apply_rgb(pixels, width, height, params.clarity, params.clarity_radius);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c)
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
        }
    }

    // Stage: Sharpen
    if (en(PipelineStage::SHARPEN) && params.sharpen != 0.0f) {
        std::vector<float> copy(pixels, pixels + static_cast<size_t>(width) * height * channels);
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
                    pixels[center] = clamp01_safe(copy[center] + (sum - copy[center]) * scale);
                }
            }
        }
    }

    // Stage: Denoise
    if (en(PipelineStage::DENOISE)) {
        // Luminance denoise (non-local means style)
        if (params.luminance_denoise_strength > 0.0f) {
            // Simple bilateral-style luminance denoise: for each pixel, blend with
            // neighboring pixels weighted by luminance similarity
            int radius = 2;
            float sigmaSpatial = 3.0f;
            float sigmaRange = 0.1f * (1.0f - params.luminance_denoise_detail) + 0.02f;
            float strength = params.luminance_denoise_strength * 0.5f;
            if (sigmaRange < 0.005f) sigmaRange = 0.005f;

            std::vector<float> copy(pixels, pixels + static_cast<size_t>(width) * height * channels);
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int centerIdx = (y * width + x) * channels;
                    float sumR = 0, sumG = 0, sumB = 0, sumW = 0;
                    float cr = copy[centerIdx], cg = copy[centerIdx + 1], cb = copy[centerIdx + 2];
                    float lumC = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb;

                    for (int dy = -radius; dy <= radius; ++dy) {
                        for (int dx = -radius; dx <= radius; ++dx) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            int nIdx = (ny * width + nx) * channels;
                            float nr = copy[nIdx], ng = copy[nIdx + 1], nb = copy[nIdx + 2];
                            float lumN = 0.2126f * nr + 0.7152f * ng + 0.0722f * nb;
                            float dist = (dx * dx + dy * dy) / (2.0f * sigmaSpatial * sigmaSpatial);
                            float lumDiff = (lumC - lumN) * (lumC - lumN) / (2.0f * sigmaRange * sigmaRange);
                            float w = std::exp(-dist - lumDiff);
                            sumR += nr * w;
                            sumG += ng * w;
                            sumB += nb * w;
                            sumW += w;
                        }
                    }
                    if (sumW > 0.0001f) {
                        pixels[centerIdx]     = clamp01_safe(cr + (sumR / sumW - cr) * strength);
                        pixels[centerIdx + 1] = clamp01_safe(cg + (sumG / sumW - cg) * strength);
                        pixels[centerIdx + 2] = clamp01_safe(cb + (sumB / sumW - cb) * strength);
                    }
                }
            }
        }

        // Chroma denoise (bilateral filter on chroma channels)
        if (params.chroma_denoise_strength > 0.0f) {
            int radius = 1;
            float sigmaSpatial = 2.0f;
            float sigmaRange = params.chroma_denoise_threshold * 0.3f + 0.02f;
            float strength = params.chroma_denoise_strength;

            std::vector<float> copy(pixels, pixels + static_cast<size_t>(width) * height * channels);
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int centerIdx = (y * width + x) * channels;
                    float cr = copy[centerIdx], cg = copy[centerIdx + 1], cb = copy[centerIdx + 2];
                    float lumC = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb;
                    // Chroma channels: Cr = R - Y, Cb = B - Y
                    float crC = cr - lumC, cbC = cb - lumC;

                    float sumCr = 0, sumCb = 0, sumW = 0;
                    for (int dy = -radius; dy <= radius; ++dy) {
                        for (int dx = -radius; dx <= radius; ++dx) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            int nIdx = (ny * width + nx) * channels;
                            float nr = copy[nIdx], ng = copy[nIdx + 1], nb = copy[nIdx + 2];
                            float lumN = 0.2126f * nr + 0.7152f * ng + 0.0722f * nb;
                            float nCr = nr - lumN, nCb = nb - lumN;
                            float dist = (dx * dx + dy * dy) / (2.0f * sigmaSpatial * sigmaSpatial);
                            float chromaDiff = ((crC - nCr) * (crC - nCr) + (cbC - nCb) * (cbC - nCb)) /
                                               (2.0f * sigmaRange * sigmaRange);
                            float w = std::exp(-dist - chromaDiff);
                            sumCr += nCr * w;
                            sumCb += nCb * w;
                            sumW += w;
                        }
                    }
                    if (sumW > 0.0001f) {
                        float newCr = crC + (sumCr / sumW - crC) * strength;
                        float newCb = cbC + (sumCb / sumW - cbC) * strength;
                        pixels[centerIdx]     = clamp01_safe(lumC + newCr);
                        pixels[centerIdx + 1] = clamp01_safe(cg + (lumC + newCr - cr) * 0.5f + (lumC + newCb - cb) * 0.5f);
                        pixels[centerIdx + 2] = clamp01_safe(lumC + newCb);
                    }
                }
            }
        }
    }

    // Stage: Effects
    if (en(PipelineStage::EFFECTS)) {
        if (params.film_grain > 0.0f) {
            auto& gen = get_film_grain_gen();
            std::normal_distribution<float> dist(0.0f, params.film_grain * 0.05f);
            size_t total = static_cast<size_t>(pixel_count) * channels;
            for (size_t i = 0; i < total; ++i) {
                pixels[i] += dist(gen);
                pixels[i] = clamp01_safe(pixels[i]);
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
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c)
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
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
                for (size_t i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    for (int c = 0; c < 3 && c < channels; ++c)
                        pixels[idx + c] = clamp01_safe(pixels[idx + c]);
                }
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

bool PipelineService::process_with_gpu_fallback(float* pixels, int width, int height, int channels,
                                                  const PipelineParams& params) {
    if (backend_ == BufferBackend::GPU_GLES || backend_ == BufferBackend::GPU_VK) {
        // Try GPU path first
        try {
            bool gpu_result = process(pixels, width, height, channels, params);
            if (gpu_result) return true;
        } catch (...) {
            LOGE("GPU pipeline failed, falling back to CPU");
        }

        // GPU failed - fall back to CPU
        LOGI("Falling back to CPU pipeline");
        BufferBackend saved = backend_;
        backend_ = BufferBackend::CPU;
        bool cpu_result = process(pixels, width, height, channels, params);
        backend_ = saved; // restore for next attempt
        return cpu_result;
    }

    // Already CPU backend
    return process(pixels, width, height, channels, params);
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
    } else if (params.raw_decode_params.demosaic_algorithm == 1) {
        AHDDemosaicOperator::demosaic_uint16(
            src, raw_width, raw_height,
            params.raw_decode_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            params.raw_decode_params.white_level,
            params.raw_decode_params.black_level);
    } else if (params.raw_decode_params.demosaic_algorithm == 2) {
        AMAZEDemosaicOperator::demosaic_uint16(
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

    if (raw_params.demosaic_algorithm == 0) {
        RCDDemosaicOperator::demosaic_uint16(
            src, raw_width, raw_height,
            raw_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            raw_params.white_level,
            raw_params.black_level);
    } else if (raw_params.demosaic_algorithm == 1) {
        AHDDemosaicOperator::demosaic_uint16(
            src, raw_width, raw_height,
            raw_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            raw_params.white_level,
            raw_params.black_level);
    } else if (raw_params.demosaic_algorithm == 2) {
        AMAZEDemosaicOperator::demosaic_uint16(
            src, raw_width, raw_height,
            raw_params.bayer_pattern,
            output_rgb, output_rgb + output_width * output_height,
            output_rgb + 2 * output_width * output_height,
            raw_params.white_level,
            raw_params.black_level);
    }

    LOGI("RAW decode: %dx%d -> %dx%d", raw_width, raw_height, output_width, output_height);
    return true;
}

void PipelineService::apply_display_transform(float* pixels, int width, int height, int channels,
                                               const DisplayTransform& transform) {
    size_t pixel_count = static_cast<size_t>(width) * height;

    if (transform.color_science == 2) { // LINEAR
        for (size_t i = 0; i < pixel_count; ++i) {
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
            pixels[idx]=clamp01_safe(r); pixels[idx+1]=clamp01_safe(g); pixels[idx+2]=clamp01_safe(b);
        }
    } else if (transform.color_science == 0) { // ACES 2.0
        // sRGB → AP0 → RRT → display color space → peak luminance → EOTF
        color_science::convert_color_space_bulk(pixels, static_cast<int>(pixel_count), channels, 0, 3);
        color_science::aces_rrt_bulk(pixels, static_cast<int>(pixel_count), channels, channels);
        int dst_space = (transform.display_color_space == 1) ? 1 :
                        (transform.display_color_space == 2) ? 2 : 0;
        color_science::convert_color_space_bulk(pixels, static_cast<int>(pixel_count), channels, 3, dst_space);
        color_science::apply_peak_luminance_scale_bulk(pixels, static_cast<int>(pixel_count), channels, transform.peak_luminance);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            float r = pixels[idx], g = pixels[idx+1], b = pixels[idx+2];
            switch (transform.eotf) {
                case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
                case 1: color_science::pq_eotf_rgb(&r, &g, &b, transform.peak_luminance); break;
                case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
                case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
                case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
            }
            pixels[idx]=clamp01_safe(r); pixels[idx+1]=clamp01_safe(g); pixels[idx+2]=clamp01_safe(b);
        }
    } else if (transform.color_science == 1) { // OpenDRT
        color_science::opendrt_tone_map_bulk(pixels, static_cast<int>(pixel_count), channels, channels);
        color_science::apply_peak_luminance_scale_bulk(pixels, static_cast<int>(pixel_count), channels, transform.peak_luminance);
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            float r = pixels[idx], g = pixels[idx+1], b = pixels[idx+2];
            switch (transform.eotf) {
                case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
                case 1: color_science::pq_eotf_rgb(&r, &g, &b, transform.peak_luminance); break;
                case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
                case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
                case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
            }
            pixels[idx]=clamp01_safe(r); pixels[idx+1]=clamp01_safe(g); pixels[idx+2]=clamp01_safe(b);
        }
    }
}

bool PipelineService::process_stage(PipelineStage stage, float* pixels, int width, int height,
                                     int channels, const PipelineParams& params) {
    size_t pixel_count = static_cast<size_t>(width) * height;
    switch (stage) {
        case PipelineStage::AUTO_EXPOSURE: {
            PipelineParams mutable_params = params;
            apply_auto_exposure(pixels, width, height, channels, mutable_params);
            break;
        }
        case PipelineStage::EXPOSURE: {
            float scale = std::pow(2.0f, params.exposure);
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c) {
                    pixels[idx + c] *= scale;
                    pixels[idx + c] = clamp01_safe(pixels[idx + c]);
                }
            }
            break;
        }
        case PipelineStage::WHITE_BALANCE: {
            color_science::planckian_white_balance_bulk(pixels, static_cast<int>(pixel_count), channels,
                                                         params.white_balance_temp,
                                                         params.white_balance_tint);
            break;
        }
        case PipelineStage::TONE: {
            float scale = 1.0f + params.contrast;
            float offset = -0.5f * scale + 0.5f;
            for (size_t i = 0; i < pixel_count; ++i) {
                int idx = i * channels;
                for (int c = 0; c < 3 && c < channels; ++c) {
                    float& v = pixels[idx + c];
                    v = v * scale + offset;
                    v = clamp01_safe(v);
                }
            }
            break;
        }
        case PipelineStage::COLOR: {
            if (params.saturation != 0.0f) {
                float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
                float s = 1.0f + params.saturation;
                for (size_t i = 0; i < pixel_count; ++i) {
                    int idx = i * channels;
                    float r = pixels[idx], g = pixels[idx + 1], b = pixels[idx + 2];
                    float lum = r * lumR + g * lumG + b * lumB;
                    pixels[idx]     = clamp01_safe(lum + (r - lum) * s);
                    pixels[idx + 1] = clamp01_safe(lum + (g - lum) * s);
                    pixels[idx + 2] = clamp01_safe(lum + (b - lum) * s);
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

void PipelineService::apply_auto_exposure(float* pixels, int width, int height, int channels,
                                           PipelineParams& params) {
    float ev = AutoExposureOperator::compute_auto_exposure(
        pixels, width, height, channels,
        params.auto_exposure_target_percentile,
        params.auto_exposure_target_luminance);

    params.auto_exposure_value = ev;
    LOGI("AutoExposure computed: %.2f EV", ev);

    // Apply the computed exposure
    if (ev != 0.0f) {
        float scale = std::pow(2.0f, ev);
        size_t pixel_count = static_cast<size_t>(width) * height;
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < 3 && c < channels; ++c) {
                pixels[idx + c] *= scale;
                pixels[idx + c] = clamp01_safe(pixels[idx + c]);
            }
        }
    }
}

float PipelineService::compute_auto_exposure(const float* pixels, int width, int height, int channels,
                                              float target_percentile, float target_luminance) {
    return AutoExposureOperator::compute_auto_exposure(
        pixels, width, height, channels, target_percentile, target_luminance);
}

// ============================================================
// PipelineSnapshot Implementation
// ============================================================

PipelineSnapshot::PipelineSnapshot(int width, int height, int channels, const PipelineParams& params)
    : width_(width), height_(height), channels_(channels), params_(params) {}

PipelineSnapshot::~PipelineSnapshot() {
    release();
}

std::unique_ptr<PipelineSnapshot> PipelineSnapshot::create(const float* pixels, int width, int height,
                                                             int channels, const PipelineParams& params) {
    auto snapshot = std::make_unique<PipelineSnapshot>(width, height, channels, params);
    size_t total = static_cast<size_t>(width) * height * channels;
    snapshot->data_.assign(pixels, pixels + total);
    return snapshot;
}

bool PipelineSnapshot::render(float* output, int output_width, int output_height) const {
    if (!is_valid() || !output) return false;
    if (output_width != width_ || output_height != height_) return false;

    // Simple copy for read-only rendering
    size_t total = static_cast<size_t>(width_) * height_ * channels_;
    std::copy(data_.data(), data_.data() + total, output);
    return true;
}

void PipelineSnapshot::release() {
    data_.clear();
    data_.shrink_to_fit();
}

} // namespace alcedo
#include "lmt_op.h"
#include <cmath>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "AlcedoLMT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

LMTOp::LMTOp() = default;

LMTOp::LMTOp(const char* lut_path) : lut_path_(lut_path) {
    LoadLUT(lut_path);
}

bool LMTOp::LoadLUT(const char* path) {
    if (ParseCubeFile(path)) {
        lut_loaded_ = true;
        LOGI("LMT LUT loaded: %s (size=%d)", path, lut_size_);
        return true;
    }
    lut_loaded_ = false;
    LOGE("Failed to load LMT LUT: %s", path);
    return false;
}

void LMTOp::SampleLUT(float r, float g, float b,
                       float* out_r, float* out_g, float* out_b) const {
    if (!lut_loaded_ || lut_size_ < 2) {
        *out_r = r;
        *out_g = g;
        *out_b = b;
        return;
    }

    float max_idx = static_cast<float>(lut_size_ - 1);

    // Clamp input to [0,1]
    r = std::clamp(r, 0.0f, 1.0f);
    g = std::clamp(g, 0.0f, 1.0f);
    b = std::clamp(b, 0.0f, 1.0f);

    // Compute fractional indices
    float r_idx = r * max_idx;
    float g_idx = g * max_idx;
    float b_idx = b * max_idx;

    int r0 = static_cast<int>(r_idx);
    int g0 = static_cast<int>(g_idx);
    int b0 = static_cast<int>(b_idx);
    int r1 = std::min(r0 + 1, lut_size_ - 1);
    int g1 = std::min(g0 + 1, lut_size_ - 1);
    int b1 = std::min(b0 + 1, lut_size_ - 1);

    float dr = r_idx - r0;
    float dg = g_idx - g0;
    float db = b_idx - b0;

    // Helper to sample a single channel from the LUT at integer indices
    auto sample = [&](int ri, int gi, int bi, int ch) -> float {
        int idx = (ri * lut_size_ * lut_size_ + gi * lut_size_ + bi) * 3 + ch;
        return lut_data_[idx];
    };

    // Trilinear interpolation for each output channel
    for (int ch = 0; ch < 3; ++ch) {
        float c000 = sample(r0, g0, b0, ch);
        float c001 = sample(r0, g0, b1, ch);
        float c010 = sample(r0, g1, b0, ch);
        float c011 = sample(r0, g1, b1, ch);
        float c100 = sample(r1, g0, b0, ch);
        float c101 = sample(r1, g0, b1, ch);
        float c110 = sample(r1, g1, b0, ch);
        float c111 = sample(r1, g1, b1, ch);

        // Interpolate along R
        float c00 = c000 * (1.0f - dr) + c100 * dr;
        float c01 = c001 * (1.0f - dr) + c101 * dr;
        float c10 = c010 * (1.0f - dr) + c110 * dr;
        float c11 = c011 * (1.0f - dr) + c111 * dr;

        // Interpolate along G
        float c0 = c00 * (1.0f - dg) + c10 * dg;
        float c1 = c01 * (1.0f - dg) + c11 * dg;

        // Interpolate along B
        float val = c0 * (1.0f - db) + c1 * db;

        if (ch == 0) *out_r = val;
        else if (ch == 1) *out_g = val;
        else *out_b = val;
    }
}

bool LMTOp::ParseCubeFile(const char* path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        LOGE("Failed to open .cube file: %s", path);
        return false;
    }

    std::string line;
    int parsed_size = 0;
    std::vector<float> raw_data;

    while (std::getline(file, line)) {
        // Remove comments
        size_t comment = line.find('#');
        if (comment != std::string::npos) {
            line = line.substr(0, comment);
        }

        // Trim trailing whitespace
        while (!line.empty() && (line.back() == ' ' || line.back() == '\t' || line.back() == '\r')) {
            line.pop_back();
        }
        // Trim leading whitespace
        size_t start = 0;
        while (start < line.size() && (line[start] == ' ' || line[start] == '\t')) {
            ++start;
        }
        if (start >= line.size()) continue;

        std::string trimmed = line.substr(start);
        std::istringstream iss(trimmed);
        std::string keyword;
        iss >> keyword;

        if (keyword == "TITLE") {
            continue; // Skip
        } else if (keyword == "DOMAIN_MIN") {
            continue; // Parsed but not used for now
        } else if (keyword == "DOMAIN_MAX") {
            continue;
        } else if (keyword == "LUT_1D_SIZE") {
            // 1D LUT - store size but mark as not 3D
            int size1d;
            iss >> size1d;
            LOGI("1D LUT detected with size %d, treating as identity for 3D", size1d);
            continue;
        } else if (keyword == "LUT_3D_SIZE") {
            iss >> parsed_size;
        } else {
            // Try to parse as data row
            float rv, gv, bv;
            std::istringstream data_iss(trimmed);
            if (data_iss >> rv >> gv >> bv) {
                raw_data.push_back(rv);
                raw_data.push_back(gv);
                raw_data.push_back(bv);
            }
        }
    }

    if (parsed_size <= 0) {
        // Auto-detect size from data
        if (raw_data.size() % 3 == 0 && !raw_data.empty()) {
            int total_entries = static_cast<int>(raw_data.size() / 3);
            int size = static_cast<int>(std::round(std::cbrt(static_cast<float>(total_entries))));
            if (size * size * size == total_entries) {
                parsed_size = size;
            } else {
                LOGE("Cannot auto-detect LUT size from %d entries", total_entries);
                return false;
            }
        } else {
            LOGE("No valid LUT data found");
            return false;
        }
    }

    int expected = parsed_size * parsed_size * parsed_size * 3;
    if (static_cast<int>(raw_data.size()) != expected) {
        LOGE("LUT size mismatch: expected %d values, got %zu", expected, raw_data.size());
        return false;
    }

    lut_size_ = parsed_size;
    lut_data_ = std::move(raw_data);
    return true;
}

void LMTOp::ApplyImpl(float* pixels, int width, int height, int channels, float intensity) {
    if (!lut_loaded_) {
        // Identity transform when no LUT is loaded
        return;
    }

    // Clamp intensity to valid range
    intensity = std::clamp(intensity, 0.0f, 1.0f);

    int total = width * height;
    for (int i = 0; i < total; ++i) {
        int idx = i * channels;
        float r = std::clamp(pixels[idx], 0.0f, 1.0f);
        float g = std::clamp(pixels[idx + 1], 0.0f, 1.0f);
        float b = std::clamp(pixels[idx + 2], 0.0f, 1.0f);

        float out_r, out_g, out_b;
        SampleLUT(r, g, b, &out_r, &out_g, &out_b);

        // Blend original with LUT result based on intensity
        pixels[idx]     = r + (out_r - r) * intensity;
        pixels[idx + 1] = g + (out_g - g) * intensity;
        pixels[idx + 2] = b + (out_b - b) * intensity;
    }
}

} // namespace alcedo

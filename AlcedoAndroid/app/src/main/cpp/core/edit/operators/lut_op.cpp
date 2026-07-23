#include "lut_op.h"
#include <cmath>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AlcedoLut"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

float LutOperator::clamp(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

float LutOperator::trilinear_lookup(const float* lut_data, int size,
                                     float r, float g, float b) {
    float max_idx = static_cast<float>(size - 1);

    float r_idx = clamp(r, 0.0f, 1.0f) * max_idx;
    float g_idx = clamp(g, 0.0f, 1.0f) * max_idx;
    float b_idx = clamp(b, 0.0f, 1.0f) * max_idx;

    int r0 = static_cast<int>(r_idx);
    int g0 = static_cast<int>(g_idx);
    int b0 = static_cast<int>(b_idx);
    int r1 = std::min(r0 + 1, size - 1);
    int g1 = std::min(g0 + 1, size - 1);
    int b1 = std::min(b0 + 1, size - 1);

    float dr = r_idx - r0;
    float dg = g_idx - g0;
    float db = b_idx - b0;

    // 8 corner values
    auto sample = [&](int ri, int gi, int bi, int ch) -> float {
        int idx = (ri * size * size + gi * size + bi) * 3 + ch;
        return lut_data[idx];
    };

    float result = 0.0f;
    // Interpolate along R first, then G, then B
    float c00 = sample(r0, g0, b0, 0) * (1.0f - dr) + sample(r1, g0, b0, 0) * dr;
    float c01 = sample(r0, g0, b1, 0) * (1.0f - dr) + sample(r1, g0, b1, 0) * dr;
    float c10 = sample(r0, g1, b0, 0) * (1.0f - dr) + sample(r1, g1, b0, 0) * dr;
    float c11 = sample(r0, g1, b1, 0) * (1.0f - dr) + sample(r1, g1, b1, 0) * dr;

    float c0 = c00 * (1.0f - dg) + c10 * dg;
    float c1 = c01 * (1.0f - dg) + c11 * dg;

    return c0 * (1.0f - db) + c1 * db;
}

// Full trilinear lookup returning all 3 channels
static void trilinear_lookup_rgb(const float* lut_data, int size,
                                  float r, float g, float b,
                                  float& r_out, float& g_out, float& b_out) {
    float max_idx = static_cast<float>(size - 1);

    float r_idx = LutOperator::clamp(r, 0.0f, 1.0f) * max_idx;
    float g_idx = LutOperator::clamp(g, 0.0f, 1.0f) * max_idx;
    float b_idx = LutOperator::clamp(b, 0.0f, 1.0f) * max_idx;

    int r0 = static_cast<int>(r_idx);
    int g0 = static_cast<int>(g_idx);
    int b0 = static_cast<int>(b_idx);
    int r1 = std::min(r0 + 1, size - 1);
    int g1 = std::min(g0 + 1, size - 1);
    int b1 = std::min(b0 + 1, size - 1);

    float dr = r_idx - r0;
    float dg = g_idx - g0;
    float db = b_idx - b0;

    auto sample = [&](int ri, int gi, int bi) -> int {
        return (ri * size * size + gi * size + bi) * 3;
    };

    for (int ch = 0; ch < 3; ++ch) {
        float c00 = lut_data[sample(r0, g0, b0) + ch] * (1.0f - dr) + lut_data[sample(r1, g0, b0) + ch] * dr;
        float c01 = lut_data[sample(r0, g0, b1) + ch] * (1.0f - dr) + lut_data[sample(r1, g0, b1) + ch] * dr;
        float c10 = lut_data[sample(r0, g1, b0) + ch] * (1.0f - dr) + lut_data[sample(r1, g1, b0) + ch] * dr;
        float c11 = lut_data[sample(r0, g1, b1) + ch] * (1.0f - dr) + lut_data[sample(r1, g1, b1) + ch] * dr;

        float c0 = c00 * (1.0f - dg) + c10 * dg;
        float c1 = c01 * (1.0f - dg) + c11 * dg;

        float val = c0 * (1.0f - db) + c1 * db;
        if (ch == 0) r_out = val;
        else if (ch == 1) g_out = val;
        else b_out = val;
    }
}

void LutOperator::apply_rgb(float* pixels, int width, int height,
                             const float* lut_data, int lut_size,
                             float input_min, float input_max,
                             float output_min, float output_max) {
    if (!lut_data || lut_size < 2) return;
    int total = width * height;

    float input_range = input_max - input_min;
    float output_range = output_max - output_min;

    for (int i = 0; i < total; ++i) {
        int idx = i * 3;
        float r = (pixels[idx] - input_min) / input_range;
        float g = (pixels[idx + 1] - input_min) / input_range;
        float b = (pixels[idx + 2] - input_min) / input_range;

        float ro, go, bo;
        trilinear_lookup_rgb(lut_data, lut_size,
                              clamp(r, 0.0f, 1.0f),
                              clamp(g, 0.0f, 1.0f),
                              clamp(b, 0.0f, 1.0f),
                              ro, go, bo);

        pixels[idx]     = std::clamp(output_min + ro * output_range, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(output_min + go * output_range, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(output_min + bo * output_range, 0.0f, 1.0f);
    }
}

void LutOperator::apply_rgba(float* pixels, int width, int height,
                              const float* lut_data, int lut_size,
                              float input_min, float input_max,
                              float output_min, float output_max) {
    if (!lut_data || lut_size < 2) return;
    int total = width * height;

    float input_range = input_max - input_min;
    float output_range = output_max - output_min;

    for (int i = 0; i < total; ++i) {
        int idx = i * 4;
        float r = (pixels[idx] - input_min) / input_range;
        float g = (pixels[idx + 1] - input_min) / input_range;
        float b = (pixels[idx + 2] - input_min) / input_range;

        float ro, go, bo;
        trilinear_lookup_rgb(lut_data, lut_size,
                              clamp(r, 0.0f, 1.0f),
                              clamp(g, 0.0f, 1.0f),
                              clamp(b, 0.0f, 1.0f),
                              ro, go, bo);

        pixels[idx]     = std::clamp(output_min + ro * output_range, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(output_min + go * output_range, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(output_min + bo * output_range, 0.0f, 1.0f);
    }
}

bool LutOperator::parse_cube_file(const std::string& path,
                                   float*& lut_data, int& lut_size) {
    std::ifstream file(path);
    if (!file.is_open()) {
        LOGE("Failed to open CUBE file: %s", path.c_str());
        return false;
    }

    std::string line;
    lut_size = 0;
    float input_min[3] = {0.0f, 0.0f, 0.0f};
    float input_max[3] = {1.0f, 1.0f, 1.0f};
    std::vector<float> raw_data;

    while (std::getline(file, line)) {
        // Remove comments
        size_t comment = line.find('#');
        if (comment != std::string::npos) {
            line = line.substr(0, comment);
        }

        // Trim whitespace
        while (!line.empty() && (line.back() == ' ' || line.back() == '\t' || line.back() == '\r')) {
            line.pop_back();
        }
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
            // Skip title
            continue;
        } else if (keyword == "DOMAIN_MIN") {
            iss >> input_min[0] >> input_min[1] >> input_min[2];
        } else if (keyword == "DOMAIN_MAX") {
            iss >> input_max[0] >> input_max[1] >> input_max[2];
        } else if (keyword == "LUT_1D_SIZE") {
            // 1D LUTs not supported for 3D application
            LOGE("1D LUTs not supported");
            return false;
        } else if (keyword == "LUT_3D_SIZE") {
            iss >> lut_size;
        } else {
            // Try to parse as data
            float r, g, b;
            std::istringstream data_iss(trimmed);
            if (data_iss >> r >> g >> b) {
                raw_data.push_back(r);
                raw_data.push_back(g);
                raw_data.push_back(b);
            }
        }
    }

    if (lut_size <= 0) {
        // Auto-detect size from data
        if (raw_data.size() % 3 == 0) {
            int total_entries = raw_data.size() / 3;
            lut_size = static_cast<int>(std::round(std::cbrt(total_entries)));
            if (lut_size * lut_size * lut_size != total_entries) {
                LOGE("Invalid LUT data size: %zu entries, not a perfect cube", raw_data.size() / 3);
                return false;
            }
        } else {
            LOGE("Invalid LUT data: %zu values not divisible by 3", raw_data.size());
            return false;
        }
    }

    int expected = lut_size * lut_size * lut_size * 3;
    if (static_cast<int>(raw_data.size()) != expected) {
        LOGE("LUT size mismatch: expected %d values, got %zu", expected, raw_data.size());
        return false;
    }

    lut_data = new float[expected];
    std::copy(raw_data.begin(), raw_data.end(), lut_data);

    LOGI("Parsed CUBE LUT: size=%d, entries=%d", lut_size, lut_size * lut_size * lut_size);
    return true;
}

void LutOperator::free_parsed_lut(float* lut_data) {
    delete[] lut_data;
}

} // namespace alcedo
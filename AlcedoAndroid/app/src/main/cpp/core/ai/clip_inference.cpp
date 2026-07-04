#include "clip_inference.h"
#include <cmath>
#include <cstring>
#include <algorithm>
#include <numeric>
#include <sstream>
#include <unordered_map>

namespace alcedo::ai {

// ── Construction ──
ClipInference::ClipInference(const Config& config) : config_(config) {}

ClipInference::~ClipInference() {
    unloadModel();
}

// ── Model lifecycle ──
bool ClipInference::loadModel() {
    std::lock_guard<std::mutex> lock(inferenceMutex_);
    if (loaded_) return true;

    // In production, this would call:
    //   Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "AlcedoClip");
    //   Ort::SessionOptions sessionOptions;
    //   sessionOptions.SetIntraOpNumThreads(config_.numThreads);
    //   if (config_.useFP16) sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_EXTENDED);
    //   session_ = std::make_unique<Ort::Session>(env, config_.modelPath.c_str(), sessionOptions);
    //
    // For now, we validate the model path is non-empty
    if (config_.modelPath.empty()) {
        return false;
    }

    loaded_ = true;
    return true;
}

void ClipInference::unloadModel() {
    std::lock_guard<std::mutex> lock(inferenceMutex_);
    loaded_ = false;
}

// ── Image preprocessing ──
std::vector<float> ClipInference::resizeAndCrop(const uint8_t* rgbData, int srcW, int srcH, int targetSize) {
    // Simple bilinear resize + center crop to targetSize×targetSize
    // This is a reference implementation; production would use SIMD or GPU

    std::vector<float> result(targetSize * targetSize * 3, 0.0f);

    // Compute crop region
    int cropSize = std::min(srcW, srcH);
    int cropX = (srcW - cropSize) / 2;
    int cropY = (srcH - cropSize) / 2;

    float scale = static_cast<float>(targetSize) / static_cast<float>(cropSize);

    for (int y = 0; y < targetSize; ++y) {
        for (int x = 0; x < targetSize; ++x) {
            float srcXf = cropX + (static_cast<float>(x) + 0.5f) / scale;
            float srcYf = cropY + (static_cast<float>(y) + 0.5f) / scale;

            int srcX = std::clamp(static_cast<int>(srcXf), 0, srcW - 1);
            int srcY = std::clamp(static_cast<int>(srcYf), 0, srcH - 1);

            int srcIdx = (srcY * srcW + srcX) * 3;
            int dstIdx = (y * targetSize + x) * 3;

            result[dstIdx + 0] = static_cast<float>(rgbData[srcIdx + 0]) / 255.0f;
            result[dstIdx + 1] = static_cast<float>(rgbData[srcIdx + 1]) / 255.0f;
            result[dstIdx + 2] = static_cast<float>(rgbData[srcIdx + 2]) / 255.0f;
        }
    }
    return result;
}

void ClipInference::normalizePixels(std::vector<float>& pixels, int size) {
    // CLIP normalization: (pixel - mean) / std
    // Mean: [0.48145466, 0.4578275, 0.40821073]
    // Std:  [0.26862954, 0.26130258, 0.27577711]
    const float meanR = 0.48145466f, meanG = 0.4578275f, meanB = 0.40821073f;
    const float stdR = 0.26862954f, stdG = 0.26130258f, stdB = 0.27577711f;

    int totalPixels = size * size;
    for (int i = 0; i < totalPixels; ++i) {
        pixels[i * 3 + 0] = (pixels[i * 3 + 0] - meanR) / stdR;
        pixels[i * 3 + 1] = (pixels[i * 3 + 1] - meanG) / stdG;
        pixels[i * 3 + 2] = (pixels[i * 3 + 2] - meanB) / stdB;
    }
}

std::vector<float> ClipInference::preprocessImage(const uint8_t* rgbData, int width, int height) const {
    auto pixels = resizeAndCrop(rgbData, width, height, config_.imageSize);
    normalizePixels(pixels, config_.imageSize);
    return pixels;
}

// ── Simple tokenizer (word-level approximation for CLIP) ──
std::vector<int64_t> ClipInference::simpleTokenize(const std::string& text, int maxLength) {
    // Simplified tokenizer: word-level with basic CLIP vocabulary mapping
    // In production, this would use the actual CLIP BPE tokenizer with
    // the full 49408-token vocabulary.

    // Map common words to approximate CLIP token IDs
    static const std::unordered_map<std::string, int64_t> vocab = {
        {"a", 320}, {"an", 385}, {"the", 518}, {"of", 539}, {"in", 530},
        {"on", 531}, {"at", 532}, {"to", 533}, {"and", 537}, {"or", 568},
        {"is", 533}, {"are", 681}, {"was", 700}, {"were", 701},
        {"photo", 8500}, {"image", 7600}, {"picture", 7800},
        {"person", 1200}, {"people", 1201}, {"man", 1202}, {"woman", 1203},
        {"portrait", 4500}, {"landscape", 5600}, {"outdoor", 6200}, {"indoor", 6300},
        {"nature", 7200}, {"city", 3400}, {"building", 3500}, {"sky", 2800},
        {"sunset", 2900}, {"sunrise", 2910}, {"night", 3100}, {"day", 3000},
        {"water", 2500}, {"mountain", 2600}, {"tree", 2700}, {"flower", 2750},
        {"animal", 1500}, {"bird", 1520}, {"cat", 1540}, {"dog", 1560},
        {"car", 1700}, {"food", 1800}, {"color", 1900}, {"black", 1910},
        {"white", 1920}, {"red", 1930}, {"blue", 1940}, {"green", 1950},
        {"light", 2100}, {"dark", 2110}, {"bright", 2120}, {"shadow", 2130},
        {"beautiful", 4000}, {"stunning", 4010}, {"amazing", 4020},
        {"good", 4100}, {"bad", 4110}, {"great", 4120}, {"nice", 4130},
    };

    std::vector<int64_t> tokens;
    tokens.reserve(maxLength);

    // Start token (SOS)
    tokens.push_back(49406);

    std::string lower;
    lower.reserve(text.size());
    for (char c : text) {
        lower += static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }

    std::istringstream iss(lower);
    std::string word;
    while (iss >> word && tokens.size() < static_cast<size_t>(maxLength - 1)) {
        auto it = vocab.find(word);
        if (it != vocab.end()) {
            tokens.push_back(it->second);
        } else {
            // Unknown word: use a hash-based fallback
            size_t hash = std::hash<std::string>{}(word);
            tokens.push_back(static_cast<int64_t>(5000 + (hash % 40000)));
        }
    }

    // End token (EOS)
    if (tokens.size() < static_cast<size_t>(maxLength)) {
        tokens.push_back(49407);
    }

    // Pad to maxLength
    while (tokens.size() < static_cast<size_t>(maxLength)) {
        tokens.push_back(0); // PAD token
    }

    return tokens;
}

std::vector<int64_t> ClipInference::tokenize(const std::string& text, int maxLength) {
    return simpleTokenize(text, maxLength);
}

// ── Inference ──
std::vector<float> ClipInference::runInference(const std::vector<float>& input, const std::string& inputName) const {
    // Placeholder for actual ONNX Runtime inference.
    // In production, this would:
    //   1. Create Ort::Value input tensor from input data
    //   2. Run session_->Run(...)
    //   3. Extract output tensor data
    //
    // For now, we return a deterministic hash-based embedding that serves as
    // a valid placeholder during development. The actual ONNX integration
    // replaces this method body.

    std::vector<float> output(config_.embeddingDim, 0.0f);

    // Compute a seed from the input data for deterministic behavior
    float seed = 0.0f;
    for (size_t i = 0; i < input.size(); ++i) {
        seed += input[i] * static_cast<float>(i + 1);
    }
    uint32_t hash = static_cast<uint32_t>(std::abs(seed) * 1000000.0f);

    // Generate a deterministic pseudo-random embedding
    for (int i = 0; i < config_.embeddingDim; ++i) {
        hash = hash * 1103515245u + 12345u;
        output[i] = (static_cast<float>(hash & 0x7FFFFFFF) / 2147483648.0f) - 1.0f;
    }

    normalize(output);
    return output;
}

ClipInferenceResult ClipInference::encodeImage(const uint8_t* rgbData, int width, int height) {
    ClipInferenceResult result;

    if (!loaded_) {
        result.success = false;
        result.errorMessage = "Model not loaded";
        return result;
    }

    try {
        auto preprocessed = preprocessImage(rgbData, width, height);
        result.imageEmbedding = runInference(preprocessed, "pixel_values");
        result.success = true;
    } catch (const std::exception& e) {
        result.success = false;
        result.errorMessage = e.what();
    }

    return result;
}

ClipInferenceResult ClipInference::encodeText(const std::string& text) {
    ClipInferenceResult result;

    if (!loaded_) {
        result.success = false;
        result.errorMessage = "Model not loaded";
        return result;
    }

    try {
        auto tokens = tokenize(text, config_.textMaxLength);
        // Convert tokens to float input for ONNX
        std::vector<float> tokenFloats(tokens.begin(), tokens.end());
        result.textEmbedding = runInference(tokenFloats, "input_ids");
        result.success = true;
    } catch (const std::exception& e) {
        result.success = false;
        result.errorMessage = e.what();
    }

    return result;
}

std::vector<std::pair<std::string, float>> ClipInference::classifyImage(
    const uint8_t* rgbData, int width, int height,
    const std::vector<std::string>& candidateLabels) {

    std::vector<std::pair<std::string, float>> results;

    if (!loaded_ || candidateLabels.empty()) return results;

    auto imgResult = encodeImage(rgbData, width, height);
    if (!imgResult.success) return results;

    results.reserve(candidateLabels.size());
    for (const auto& label : candidateLabels) {
        auto txtResult = encodeText(label);
        if (!txtResult.success) {
            results.emplace_back(label, 0.0f);
            continue;
        }
        float sim = similarity(imgResult.imageEmbedding.data(),
                              txtResult.textEmbedding.data(),
                              config_.embeddingDim);
        results.emplace_back(label, sim);
    }

    std::sort(results.begin(), results.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });

    return results;
}

float ClipInference::similarity(const float* imgEmb, const float* txtEmb, int dim) {
    return cosineSimilarity(imgEmb, txtEmb, dim);
}

} // namespace alcedo::ai
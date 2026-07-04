#include "clip_inference.h"
#ifdef HAS_ONNXRUNTIME
#include <onnxruntime_cxx_api.h>
#endif
#include <cmath>
#include <cstring>
#include <algorithm>
#include <numeric>
#include <sstream>
#include <unordered_map>
#include <stdexcept>

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

    if (config_.modelPath.empty()) {
        return false;
    }

#ifdef HAS_ONNXRUNTIME
    try {
        // Create ORT environment (one per process; safe to recreate on reload)
        env_ = Ort::Env{ORT_LOGGING_LEVEL_WARNING, "AlcedoClip"};

        // Configure session options
        sessionOptions_ = Ort::SessionOptions{};

        sessionOptions_.SetIntraOpNumThreads(config_.numThreads);
        sessionOptions_.SetInterOpNumThreads(1);
        sessionOptions_.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_EXTENDED);

        if (config_.useFP16) {
            // Enable FP16 where supported
            OrtSessionOptionsAppendExecutionProvider(sessionOptions_, /*device_id=*/0);
        }

        // Create the session from the model file
        // Ort::Session expects a wide string on Windows; use the portable helper.
        session_ = std::make_unique<Ort::Session>(env_, config_.modelPath.c_str(), sessionOptions_);

        loaded_ = true;
        return true;
    } catch (const Ort::Exception& e) {
        session_.reset();
        loaded_ = false;
        return false;
    } catch (const std::exception& e) {
        session_.reset();
        loaded_ = false;
        return false;
    }
#else
    // ONNX Runtime C++ API not available; inference is handled by the Kotlin layer
    return false;
#endif
}

void ClipInference::unloadModel() {
    std::lock_guard<std::mutex> lock(inferenceMutex_);
#ifdef HAS_ONNXRUNTIME
    session_.reset();
    sessionOptions_ = Ort::SessionOptions{};
    env_ = Ort::Env{nullptr};
#endif
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
#ifdef HAS_ONNXRUNTIME
    if (!session_) {
        throw std::runtime_error("ONNX Runtime session is not initialized");
    }

    Ort::AllocatorWithDefaultOptions allocator;

    // ── Determine input shape ──
    // For image input ("pixel_values"): shape is [1, 3, imageSize, imageSize]
    // For text input ("input_ids"): shape is [1, textMaxLength]
    std::vector<int64_t> inputShape;
    if (inputName == "pixel_values") {
        inputShape = {1, 3, static_cast<int64_t>(config_.imageSize), static_cast<int64_t>(config_.imageSize)};
    } else {
        inputShape = {1, static_cast<int64_t>(config_.textMaxLength)};
    }

    size_t inputTensorSize = input.size();
    if (inputTensorSize == 0) {
        throw std::runtime_error("Input data is empty");
    }

    // ── Create input tensor ──
    auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    auto inputTensor = Ort::Value::CreateTensor<float>(
        memoryInfo,
        const_cast<float*>(input.data()),   // ORT does not modify input data
        inputTensorSize,
        inputShape.data(),
        inputShape.size()
    );

    // ── Resolve model input / output names ──
    // Use the provided inputName; fall back to the model's first input if needed.
    auto inputNameAlloc = session_->GetInputNameAllocated(0, allocator);
    std::string resolvedInputName = inputName;
    if (resolvedInputName.empty()) {
        resolvedInputName = inputNameAlloc.get();
    }

    const char* inputNames[] = { resolvedInputName.c_str() };

    // Assume a single output node
    auto outputNameAlloc = session_->GetOutputNameAllocated(0, allocator);
    const char* outputNames[] = { outputNameAlloc.get() };

    // ── Run inference ──
    auto outputTensors = session_->Run(
        Ort::RunOptions{nullptr},
        inputNames,
        &inputTensor,
        1,              // number of inputs
        outputNames,
        1               // number of outputs
    );

    // ── Extract output data ──
    if (outputTensors.empty()) {
        throw std::runtime_error("ONNX Runtime returned no output tensors");
    }

    auto& outputTensor = outputTensors[0];
    auto outputType = outputTensor.GetTypeInfo();
    auto tensorInfo = outputType.GetTensorTypeAndShapeInfo();
    size_t outputSize = tensorInfo.GetElementCount();

    const float* outputData = outputTensor.GetTensorData<float>();
    if (!outputData) {
        throw std::runtime_error("Failed to retrieve output tensor data");
    }

    // Copy output to a std::vector and L2-normalize the embedding
    std::vector<float> output(outputData, outputData + outputSize);
    normalize(output);

    return output;
#else
    (void)input;
    (void)inputName;
    throw std::runtime_error("ONNX Runtime C++ API not available; inference is handled by the Kotlin layer");
#endif
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
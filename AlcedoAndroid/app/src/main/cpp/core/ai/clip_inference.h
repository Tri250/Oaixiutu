#ifndef ALCEDO_AI_CLIP_INFERENCE_H
#define ALCEDO_AI_CLIP_INFERENCE_H

#include "embedding_utils.h"
#include <vector>
#include <string>
#include <memory>
#include <mutex>
#include <onnxruntime_cxx_api.h>

namespace alcedo::ai {

// ── Inference result ──
struct ClipInferenceResult {
    std::vector<float> imageEmbedding;
    std::vector<float> textEmbedding;
    bool success = false;
    std::string errorMessage;
};

// ── ONNX Runtime CLIP/SigLIP Inference Wrapper ──
//
// This class wraps ONNX Runtime to perform CLIP/SigLIP model inference on-device.
// It supports:
//   - Image encoding (preprocess image → embedding)
//   - Text encoding (tokenize text → embedding)
//   - Zero-shot image classification (image + candidate labels → best label)
//   - Cosine similarity scoring between image and text embeddings
//
// The model files (.onnx) are expected to be pre-downloaded and stored locally.
// This implementation uses ONNX Runtime's C API via a thin wrapper, preparing
// for the actual ORT integration when the library is linked.
//
class ClipInference {
public:
    struct Config {
        std::string modelPath;           // Path to ONNX model file
        int imageSize = 224;             // Input image resolution
        int embeddingDim = 512;          // Output embedding dimension
        int textMaxLength = 77;          // Max text token length
        bool useFP16 = false;            // Use FP16 precision
        int numThreads = 4;              // Number of inference threads
        std::string backend = "CPU";     // "CPU", "NNAPI", "XNNPACK"
    };

    explicit ClipInference(const Config& config);
    ~ClipInference();

    ClipInference(const ClipInference&) = delete;
    ClipInference& operator=(const ClipInference&) = delete;

    // ── Model lifecycle ──
    bool loadModel();
    bool isLoaded() const { return loaded_; }
    void unloadModel();
    int getEmbeddingDim() const { return config_.embeddingDim; }

    // ── Inference ──
    // Encode an image (preprocessed RGB pixel data) → normalized embedding
    ClipInferenceResult encodeImage(const uint8_t* rgbData, int width, int height);

    // Encode a text string → normalized embedding
    ClipInferenceResult encodeText(const std::string& text);

    // Zero-shot classification: return (label, score) pairs sorted by score descending
    std::vector<std::pair<std::string, float>> classifyImage(
        const uint8_t* rgbData, int width, int height,
        const std::vector<std::string>& candidateLabels);

    // Compute cosine similarity between image and text embeddings
    static float similarity(const float* imgEmb, const float* txtEmb, int dim);

    // ── Text tokenization (simple BPE-like tokenizer for CLIP) ──
    static std::vector<int64_t> tokenize(const std::string& text, int maxLength = 77);

private:
    Config config_;
    bool loaded_ = false;
    std::mutex inferenceMutex_;

    // ── Internal helpers ──
    // Preprocess image: resize, center-crop, normalize to model input
    std::vector<float> preprocessImage(const uint8_t* rgbData, int width, int height) const;

    // Run ONNX Runtime inference
    std::vector<float> runInference(const std::vector<float>& input, const std::string& inputName) const;

    // Simple tokenizer (word-level with CLIP vocab approximations)
    static std::vector<int64_t> simpleTokenize(const std::string& text, int maxLength);

    // Resize and center-crop to square
    static std::vector<float> resizeAndCrop(const uint8_t* rgbData, int srcW, int srcH, int targetSize);

    // Normalize pixel values (mean/std for CLIP)
    static void normalizePixels(std::vector<float>& pixels, int size);

    // ── ONNX Runtime members ──
    Ort::Env env_{nullptr};
    std::unique_ptr<Ort::Session> session_;
    Ort::SessionOptions sessionOptions_;
    Ort::AllocatorWithDefaultOptions allocator_;
};

} // namespace alcedo::ai

#endif // ALCEDO_AI_CLIP_INFERENCE_H
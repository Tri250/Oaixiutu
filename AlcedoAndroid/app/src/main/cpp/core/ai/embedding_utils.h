#ifndef ALCEDO_AI_EMBEDDING_UTILS_H
#define ALCEDO_AI_EMBEDDING_UTILS_H

#include <vector>
#include <cmath>
#include <string>
#include <algorithm>
#include <numeric>

namespace alcedo::ai {

constexpr int kDefaultEmbeddingDim = 512;
constexpr float kEpsilon = 1e-8f;

// L2 normalize a vector in-place
inline void normalize(float* vec, int dim) {
    float sumSq = 0.0f;
    for (int i = 0; i < dim; ++i) {
        sumSq += vec[i] * vec[i];
    }
    float norm = std::sqrt(sumSq);
    if (norm > kEpsilon) {
        float invNorm = 1.0f / norm;
        for (int i = 0; i < dim; ++i) {
            vec[i] *= invNorm;
        }
    }
}

// L2 normalize a std::vector<float> in-place
inline void normalize(std::vector<float>& vec) {
    normalize(vec.data(), static_cast<int>(vec.size()));
}

// Compute cosine similarity between two normalized vectors
inline float cosineSimilarity(const float* a, const float* b, int dim) {
    float dot = 0.0f;
    for (int i = 0; i < dim; ++i) {
        dot += a[i] * b[i];
    }
    return dot; // Assumes vectors are already normalized
}

// Compute cosine similarity between two std::vector<float>
inline float cosineSimilarity(const std::vector<float>& a, const std::vector<float>& b) {
    if (a.size() != b.size()) return 0.0f;
    return cosineSimilarity(a.data(), b.data(), static_cast<int>(a.size()));
}

// Compute dot product with L2 normalization on-the-fly
inline float cosineSimilarityRaw(const float* a, const float* b, int dim) {
    float dot = 0.0f, normA = 0.0f, normB = 0.0f;
    for (int i = 0; i < dim; ++i) {
        dot += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    float denom = std::sqrt(normA) * std::sqrt(normB);
    return (denom > kEpsilon) ? (dot / denom) : 0.0f;
}

// Element-wise addition: result = a + b
inline void addVectors(const float* a, const float* b, float* result, int dim) {
    for (int i = 0; i < dim; ++i) {
        result[i] = a[i] + b[i];
    }
}

// Element-wise subtraction: result = a - b
inline void subtractVectors(const float* a, const float* b, float* result, int dim) {
    for (int i = 0; i < dim; ++i) {
        result[i] = a[i] - b[i];
    }
}

// Scalar multiply: result = vec * scalar
inline void scaleVector(const float* vec, float scalar, float* result, int dim) {
    for (int i = 0; i < dim; ++i) {
        result[i] = vec[i] * scalar;
    }
}

// Compute L2 distance between two vectors
inline float l2Distance(const float* a, const float* b, int dim) {
    float sumSq = 0.0f;
    for (int i = 0; i < dim; ++i) {
        float diff = a[i] - b[i];
        sumSq += diff * diff;
    }
    return std::sqrt(sumSq);
}

// Compute inner product (-1 for NNS libraries that minimize distance)
inline float innerProduct(const float* a, const float* b, int dim) {
    float dot = 0.0f;
    for (int i = 0; i < dim; ++i) {
        dot += a[i] * b[i];
    }
    return -dot; // Negative so minimization = maximization
}

} // namespace alcedo::ai

#endif // ALCEDO_AI_EMBEDDING_UTILS_H
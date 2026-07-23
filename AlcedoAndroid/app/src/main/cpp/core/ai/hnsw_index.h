#ifndef ALCEDO_AI_HNSW_INDEX_H
#define ALCEDO_AI_HNSW_INDEX_H

#include "embedding_utils.h"
#include <vector>
#include <queue>
#include <unordered_map>
#include <unordered_set>
#include <random>
#include <limits>
#include <algorithm>
#include <mutex>
#include <memory>
#include <cstdint>

namespace alcedo::ai {

// ── HNSW Hyperparameters ──
struct HnswConfig {
    int M = 16;                    // Max outbound connections per node per layer
    int M_max = 16;                // For layer 0
    int M_max0 = 32;               // For layer 0 (usually 2*M)
    int efConstruction = 200;      // Search width during construction
    int efSearch = 50;             // Search width during query
    int mL = 5;                    // Level generation normalization factor
    float levelMult = 1.0f / std::log(1.0f * 16); // 1/ln(M)
};

// ── Node in the HNSW multi-layer graph ──
struct HnswNode {
    uint64_t id;                           // Original ID (imageId)
    std::vector<float> embedding;          // Feature vector (dim = embeddingDim)
    int level;                             // Topmost layer this node belongs to
    // For each layer [0..level], a list of neighbor node IDs
    std::vector<std::vector<uint64_t>> neighbors;

    HnswNode() : id(0), level(-1) {}
    HnswNode(uint64_t id, const float* emb, int dim, int level)
        : id(id), embedding(emb, emb + dim), level(level) {
        neighbors.resize(level + 1);
    }
};

// ── HNSW Index for fast approximate nearest-neighbor search ──
//
// Implements the Hierarchical Navigable Small World graph algorithm (Malkov & Yashunin, 2018).
// This is a self-contained, header-only C++ implementation suitable for on-device Android usage.
//
class HnswIndex {
public:
    explicit HnswIndex(int embeddingDim = kDefaultEmbeddingDim, const HnswConfig& config = HnswConfig());
    ~HnswIndex() = default;

    // ── Lifecycle ──
    void clear();
    bool empty() const { return nodes_.empty(); }
    size_t size() const { return nodes_.size(); }
    int getEmbeddingDim() const { return embeddingDim_; }

    // ── Insertion ──
    void insert(uint64_t id, const float* embedding);

    // ── Bulk insert for efficiency ──
    void insertBatch(const std::vector<std::pair<uint64_t, std::vector<float>>>& entries);

    // ── Search ──
    // Returns top-K nearest neighbors as (id, distance) pairs, sorted by distance ascending.
    struct SearchResult {
        uint64_t id;
        float distance; // Lower = closer (cosine distance = 1 - cosineSimilarity)
    };
    std::vector<SearchResult> search(const float* queryEmbedding, int k) const;

    // ── Removal ──
    void remove(uint64_t id);

    // ── Serialization (for persistence) ──
    std::vector<uint8_t> serialize() const;
    bool deserialize(const std::vector<uint8_t>& data);

    // ── Debug / introspection ──
    int getMaxLevel() const { return maxLevel_; }
    float getEntryPointDistance(const float* query) const;

private:
    int embeddingDim_;
    HnswConfig config_;

    std::unordered_map<uint64_t, std::unique_ptr<HnswNode>> nodes_;
    uint64_t entryPointId_ = 0;
    bool hasEntry_ = false;  // True when an entry point has been set (separate from id==0)
    int maxLevel_ = -1;

    mutable std::mt19937 rng_;
    mutable std::mutex mutex_;

    // ── Internal helpers ──
    int randomLevel() const;
    float distance(const float* a, const float* b) const;
    float distance(const std::vector<float>& a, const std::vector<float>& b) const;

    // Search layer: return ef nearest neighbors on the given layer
    std::vector<std::pair<float, uint64_t>> searchLayer(
        const float* query, const std::vector<uint64_t>& entryPoints,
        int ef, int layer) const;

    // Select neighbors: return M nearest from candidates
    std::vector<uint64_t> selectNeighbors(
        const float* query, const std::vector<uint64_t>& candidates,
        int M, int layer) const;

    // Connect new node to neighbors at given layer
    void connectNeighbors(uint64_t nodeId, int layer);
};

} // namespace alcedo::ai

#endif // ALCEDO_AI_HNSW_INDEX_H
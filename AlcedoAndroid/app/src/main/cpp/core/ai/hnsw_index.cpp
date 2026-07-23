#include "hnsw_index.h"
#include <cmath>
#include <cstring>

namespace alcedo::ai {

// ── Construction ──
HnswIndex::HnswIndex(int embeddingDim, const HnswConfig& config)
    : embeddingDim_(embeddingDim), config_(config), rng_(std::random_device{}()) {
    // Normalize levelMult
    config_.levelMult = 1.0f / std::log(1.0f * config_.M);
    config_.M_max = config_.M;
    config_.M_max0 = config_.M * 2;
}

void HnswIndex::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    nodes_.clear();
    entryPointId_ = 0;
    hasEntry_ = false;
    maxLevel_ = -1;
}

// ── Distance computation (1 - cosine) ──
float HnswIndex::distance(const float* a, const float* b) const {
    float sim = cosineSimilarity(a, b, embeddingDim_);
    return 1.0f - sim; // Range [0, 2]
}

float HnswIndex::distance(const std::vector<float>& a, const std::vector<float>& b) const {
    return distance(a.data(), b.data());
}

// ── Random level generation (geometric distribution) ──
int HnswIndex::randomLevel() const {
    std::uniform_real_distribution<float> dist(0.0f, 1.0f);
    float r = dist(rng_);
    return static_cast<int>(-std::log(r) * config_.levelMult);
}

// ── Layer search ──
std::vector<std::pair<float, uint64_t>> HnswIndex::searchLayer(
    const float* query, const std::vector<uint64_t>& entryPoints,
    int ef, int layer) const {

    // Min-heap of (distance, id) for ef nearest
    // We want to keep the closest, so we use a max-heap and pop when > ef
    using DistPair = std::pair<float, uint64_t>;
    auto cmp = [](const DistPair& a, const DistPair& b) { return a.first < b.first; };
    std::priority_queue<DistPair, std::vector<DistPair>, decltype(cmp)> candidates(cmp);

    // For the result, keep the ef closest
    std::priority_queue<DistPair> result; // max-heap (default: largest first)

    std::unordered_set<uint64_t> visited;

    for (uint64_t epId : entryPoints) {
        if (visited.count(epId)) continue;
        visited.insert(epId);

        auto it = nodes_.find(epId);
        if (it == nodes_.end()) continue;

        float d = distance(query, it->second->embedding.data());
        candidates.emplace(-d, epId); // negate for min-heap behavior
        result.emplace(d, epId);
    }

    while (!candidates.empty()) {
        auto [negDist, cId] = candidates.top();
        candidates.pop();
        float cDist = -negDist;

        // If the worst result is better than this candidate, we're done
        if (!result.empty() && result.top().first < cDist) break;

        auto it = nodes_.find(cId);
        if (it == nodes_.end()) continue;

        const auto& neighbors = it->second->neighbors[layer];
        for (uint64_t nId : neighbors) {
            if (visited.count(nId)) continue;
            visited.insert(nId);

            auto nit = nodes_.find(nId);
            if (nit == nodes_.end()) continue;

            float d = distance(query, nit->second->embedding.data());
            if (result.size() < static_cast<size_t>(ef) || d < result.top().first) {
                candidates.emplace(-d, nId);
                result.emplace(d, nId);
                if (result.size() > static_cast<size_t>(ef)) {
                    result.pop();
                }
            }
        }
    }

    // Convert to sorted vector (closest first)
    std::vector<std::pair<float, uint64_t>> sortedResult;
    sortedResult.reserve(result.size());
    while (!result.empty()) {
        sortedResult.push_back(result.top());
        result.pop();
    }
    std::sort(sortedResult.begin(), sortedResult.end(),
              [](const auto& a, const auto& b) { return a.first < b.first; });

    return sortedResult;
}

// ── Select neighbors ──
std::vector<uint64_t> HnswIndex::selectNeighbors(
    const float* query, const std::vector<uint64_t>& candidates,
    int M, int layer) const {

    std::vector<std::pair<float, uint64_t>> sorted;
    sorted.reserve(candidates.size());
    for (uint64_t cId : candidates) {
        auto it = nodes_.find(cId);
        if (it == nodes_.end()) continue;
        float d = distance(query, it->second->embedding.data());
        sorted.emplace_back(d, cId);
    }
    std::sort(sorted.begin(), sorted.end(),
              [](const auto& a, const auto& b) { return a.first < b.first; });

    std::vector<uint64_t> selected;
    selected.reserve(std::min(static_cast<size_t>(M), sorted.size()));
    for (size_t i = 0; i < sorted.size() && selected.size() < static_cast<size_t>(M); ++i) {
        selected.push_back(sorted[i].second);
    }
    return selected;
}

// ── Connect neighbors ──
void HnswIndex::connectNeighbors(uint64_t nodeId, int layer) {
    auto nodeIt = nodes_.find(nodeId);
    if (nodeIt == nodes_.end()) return;

    const float* nodeEmb = nodeIt->second->embedding.data();
    auto& neighbors = nodeIt->second->neighbors[layer];

    int maxConn = (layer == 0) ? config_.M_max0 : config_.M_max;

    // For each neighbor, also add the reverse connection
    std::vector<uint64_t> newNeighbors;
    for (uint64_t nId : neighbors) {
        auto nit = nodes_.find(nId);
        if (nit == nodes_.end()) continue;

        // Check if we should add nodeId to nId's neighbors
        if (nit->second->neighbors[layer].size() < static_cast<size_t>(maxConn)) {
            nit->second->neighbors[layer].push_back(nodeId);
        } else {
            // Prune: replace the farthest neighbor if the new node is closer
            // Distance should be from nId's perspective: distance(nId, neighbor)
            const float* nEmb = nit->second->embedding.data();
            auto& nNeighbors = nit->second->neighbors[layer];

            size_t worstIdx = 0;
            float worstDist = distance(nEmb, nodes_[nNeighbors[0]]->embedding.data());
            for (size_t j = 1; j < nNeighbors.size(); ++j) {
                float d = distance(nEmb, nodes_[nNeighbors[j]]->embedding.data());
                if (d > worstDist) {
                    worstDist = d;
                    worstIdx = j;
                }
            }
            float dToN = distance(nEmb, nodeEmb);
            if (dToN < worstDist) {
                nNeighbors[worstIdx] = nodeId;
            }
        }
    }
}

// ── Insert ──
void HnswIndex::insert(uint64_t id, const float* embedding) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Normalize the embedding
    std::vector<float> normalizedEmb(embedding, embedding + embeddingDim_);
    normalize(normalizedEmb);

    int level = randomLevel();
    auto node = std::make_unique<HnswNode>(id, normalizedEmb.data(), embeddingDim_, level);
    nodes_[id] = std::move(node);

    if (!hasEntry_) {
        entryPointId_ = id;
        hasEntry_ = true;
        maxLevel_ = level;
        return;
    }

    // Find the entry point for insertion
    uint64_t curId = entryPointId_;
    int curLevel = maxLevel_;

    // Greedy descent from top layer
    for (int L = curLevel; L > level; --L) {
        std::vector<uint64_t> entry = {curId};
        auto results = searchLayer(normalizedEmb.data(), entry, 1, L);
        if (!results.empty()) {
            curId = results[0].second;
        }
    }

    // Insert into each layer from level down to 0
    for (int L = std::min(level, curLevel); L >= 0; --L) {
        std::vector<uint64_t> entry = {curId};
        int ef = config_.efConstruction;
        auto results = searchLayer(normalizedEmb.data(), entry, ef, L);

        auto selected = selectNeighbors(normalizedEmb.data(),
            [&]() {
                std::vector<uint64_t> ids;
                for (auto& r : results) ids.push_back(r.second);
                return ids;
            }(),
            (L == 0) ? config_.M_max0 : config_.M_max, L);

        auto& node = nodes_[id];
        node->neighbors[L] = selected;
        connectNeighbors(id, L);

        if (!results.empty()) {
            curId = results[0].second;
        }
    }

    if (level > maxLevel_) {
        maxLevel_ = level;
        entryPointId_ = id;
    }
}

// ── Bulk insert ──
void HnswIndex::insertBatch(const std::vector<std::pair<uint64_t, std::vector<float>>>& entries) {
    for (const auto& [id, emb] : entries) {
        insert(id, emb.data());
    }
}

// ── Search ──
std::vector<HnswIndex::SearchResult> HnswIndex::search(const float* queryEmbedding, int k) const {
    std::lock_guard<std::mutex> lock(mutex_);

    if (nodes_.empty() || k <= 0) return {};

    // Normalize query
    std::vector<float> query(queryEmbedding, queryEmbedding + embeddingDim_);
    normalize(query);

    if (!hasEntry_) return {};

    uint64_t curId = entryPointId_;
    // Greedy descent from top
    for (int L = maxLevel_; L > 0; --L) {
        std::vector<uint64_t> entry = {curId};
        auto results = searchLayer(query.data(), entry, 1, L);
        if (!results.empty()) {
            curId = results[0].second;
        }
    }

    // Search layer 0 with efSearch
    std::vector<uint64_t> entry = {curId};
    auto results = searchLayer(query.data(), entry, config_.efSearch, 0);

    std::vector<SearchResult> finalResults;
    finalResults.reserve(std::min(static_cast<size_t>(k), results.size()));
    for (size_t i = 0; i < results.size() && finalResults.size() < static_cast<size_t>(k); ++i) {
        finalResults.push_back({results[i].second, results[i].first});
    }
    return finalResults;
}

// ── Remove ──
void HnswIndex::remove(uint64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);

    auto it = nodes_.find(id);
    if (it == nodes_.end()) return;

    // Remove references from all neighbors
    for (int L = 0; L <= it->second->level; ++L) {
        for (uint64_t nId : it->second->neighbors[L]) {
            auto nit = nodes_.find(nId);
            if (nit == nodes_.end()) continue;
            auto& nNeighbors = nit->second->neighbors[L];
            nNeighbors.erase(
                std::remove(nNeighbors.begin(), nNeighbors.end(), id),
                nNeighbors.end());
        }
    }

    nodes_.erase(it);

    if (id == entryPointId_) {
        if (nodes_.empty()) {
            entryPointId_ = 0;
            hasEntry_ = false;
            maxLevel_ = -1;
        } else {
            entryPointId_ = nodes_.begin()->first;
            maxLevel_ = nodes_.begin()->second->level;
        }
    }
}

// ── Serialization ──
std::vector<uint8_t> HnswIndex::serialize() const {
    std::lock_guard<std::mutex> lock(mutex_);

    std::vector<uint8_t> data;
    // Header: embeddingDim (4 bytes), maxLevel (4 bytes), entryPointId (8 bytes), nodeCount (4 bytes)
    auto appendInt = [&](auto val) {
        const uint8_t* bytes = reinterpret_cast<const uint8_t*>(&val);
        data.insert(data.end(), bytes, bytes + sizeof(val));
    };

    appendInt(static_cast<int32_t>(embeddingDim_));
    appendInt(static_cast<int32_t>(maxLevel_));
    appendInt(static_cast<uint64_t>(entryPointId_));
    appendInt(static_cast<int32_t>(nodes_.size()));

    for (const auto& [id, node] : nodes_) {
        appendInt(static_cast<uint64_t>(id));
        appendInt(static_cast<int32_t>(node->level));
        // Embedding
        appendInt(static_cast<int32_t>(embeddingDim_));
        for (float v : node->embedding) {
            appendInt(v);
        }
        // Neighbors per layer
        for (int L = 0; L <= node->level; ++L) {
            appendInt(static_cast<int32_t>(node->neighbors[L].size()));
            for (uint64_t nId : node->neighbors[L]) {
                appendInt(static_cast<uint64_t>(nId));
            }
        }
    }
    return data;
}

bool HnswIndex::deserialize(const std::vector<uint8_t>& data) {
    std::lock_guard<std::mutex> lock(mutex_);

    clear();
    if (data.size() < 20) return false;

    size_t offset = 0;
    auto readVal = [&](auto& val) {
        if (offset + sizeof(val) > data.size()) return false;
        std::memcpy(&val, data.data() + offset, sizeof(val));
        offset += sizeof(val);
        return true;
    };

    int32_t dim, maxLvl, nodeCount;
    uint64_t epId;

    if (!readVal(dim) || !readVal(maxLvl) || !readVal(epId) || !readVal(nodeCount)) {
        return false;
    }

    embeddingDim_ = dim;
    maxLevel_ = maxLvl;
    entryPointId_ = epId;
    hasEntry_ = (nodeCount > 0);

    for (int32_t i = 0; i < nodeCount; ++i) {
        uint64_t id;
        int32_t level;
        int32_t embDim;

        if (!readVal(id) || !readVal(level) || !readVal(embDim)) return false;
        if (embDim != embeddingDim_) return false;

        std::vector<float> emb(embDim);
        for (int32_t j = 0; j < embDim; ++j) {
            if (!readVal(emb[j])) return false;
        }

        auto node = std::make_unique<HnswNode>(id, emb.data(), embDim, level);
        for (int32_t L = 0; L <= level; ++L) {
            int32_t nCount;
            if (!readVal(nCount)) return false;
            node->neighbors[L].reserve(nCount);
            for (int32_t j = 0; j < nCount; ++j) {
                uint64_t nId;
                if (!readVal(nId)) return false;
                node->neighbors[L].push_back(nId);
            }
        }
        nodes_[id] = std::move(node);
    }

    return true;
}

float HnswIndex::getEntryPointDistance(const float* query) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!hasEntry_) return std::numeric_limits<float>::max();
    auto it = nodes_.find(entryPointId_);
    if (it == nodes_.end()) return std::numeric_limits<float>::max();
    return distance(query, it->second->embedding.data());
}

} // namespace alcedo::ai
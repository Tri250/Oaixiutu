#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <functional>
#include <memory>
#include <unordered_map>
#include <mutex>

namespace alcedo {

// ============================================================
// Thumbnail resolution tier
// ============================================================
enum class ThumbnailSize {
    SMALL = 0,   // 128px max
    MEDIUM = 1,  // 256px max
    LARGE = 2,   // 512px max
    XLARGE = 3,  // 1024px max
    ORIGINAL = 4, // Full preview
};

// ============================================================
// Thumbnail generation options
// ============================================================
struct ThumbnailOptions {
    ThumbnailSize size = ThumbnailSize::MEDIUM;
    int max_dimension = 256;
    int quality = 85;               // JPEG quality 1-100
    bool use_embedded = true;       // Use embedded thumbnail if available
    bool generate_from_raw = true;  // Generate from RAW if no embedded
    bool use_disk_cache = true;
    bool use_memory_cache = true;
    std::string cache_dir;
};

// ============================================================
// Thumbnail result
// ============================================================
struct ThumbnailResult {
    std::vector<uint8_t> data;      // JPEG or raw RGB data
    int width = 0;
    int height = 0;
    ThumbnailSize size = ThumbnailSize::MEDIUM;
    bool is_embedded = false;
    bool is_generated = false;
    bool success = false;
    std::string error_message;
    std::string source_path;        // Original file path
};

// ============================================================
// LRU in-memory cache
// ============================================================
template<typename K, typename V>
class LruCache {
public:
    explicit LruCache(size_t max_size) : max_size_(max_size) {}

    bool get(const K& key, V& value) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it == map_.end()) return false;
        // Move to front
        list_.splice(list_.begin(), list_, it->second);
        value = it->second->second;
        return true;
    }

    void put(const K& key, const V& value) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it != map_.end()) {
            it->second->second = value;
            list_.splice(list_.begin(), list_, it->second);
            return;
        }
        if (list_.size() >= max_size_) {
            auto last = list_.back();
            map_.erase(last.first);
            list_.pop_back();
        }
        list_.emplace_front(key, value);
        map_[key] = list_.begin();
    }

    bool contains(const K& key) {
        std::lock_guard<std::mutex> lock(mutex_);
        return map_.find(key) != map_.end();
    }

    void remove(const K& key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it != map_.end()) {
            list_.erase(it->second);
            map_.erase(it);
        }
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        list_.clear();
        map_.clear();
    }

    size_t size() const { return list_.size(); }

private:
    size_t max_size_;
    std::list<std::pair<K, V>> list_;
    std::unordered_map<K, typename std::list<std::pair<K, V>>::iterator> map_;
    std::mutex mutex_;
};

// ============================================================
// Async thumbnail generation callback
// ============================================================
using ThumbnailCallback = std::function<void(const ThumbnailResult& result)>;
using ThumbnailProgressCallback = std::function<void(float progress, const std::string& stage)>;

// ============================================================
// Thumbnail Decoder
// ============================================================
class ThumbnailDecoder {
public:
    ThumbnailDecoder();
    ~ThumbnailDecoder();

    // ── Configuration ──
    void set_cache_dir(const std::string& dir);
    void set_memory_cache_size(size_t max_entries);
    void set_default_options(const ThumbnailOptions& options);

    // ── Synchronous thumbnail generation ──
    ThumbnailResult generate(const std::string& file_path,
                              const ThumbnailOptions& options = ThumbnailOptions());
    ThumbnailResult generate_from_memory(const uint8_t* data, size_t size,
                                          const std::string& source_id,
                                          const ThumbnailOptions& options = ThumbnailOptions());

    // ── Async thumbnail generation ──
    void generate_async(const std::string& file_path,
                         ThumbnailCallback callback,
                         const ThumbnailOptions& options = ThumbnailOptions());
    void generate_async_from_memory(const uint8_t* data, size_t size,
                                     const std::string& source_id,
                                     ThumbnailCallback callback,
                                     const ThumbnailOptions& options = ThumbnailOptions());

    // ── Batch generation ──
    void generate_batch(const std::vector<std::string>& file_paths,
                         ThumbnailCallback per_item_callback,
                         const ThumbnailOptions& options = ThumbnailOptions());

    // ── Cache operations ──
    bool load_from_disk_cache(const std::string& source_id,
                               ThumbnailSize size, ThumbnailResult& result);
    bool save_to_disk_cache(const std::string& source_id,
                             const ThumbnailResult& result);
    bool load_from_memory_cache(const std::string& source_id,
                                 ThumbnailSize size, ThumbnailResult& result);
    void save_to_memory_cache(const std::string& source_id,
                               const ThumbnailResult& result);

    // ── Cache management ──
    void clear_memory_cache();
    void clear_disk_cache();
    void evict_from_memory_cache(const std::string& source_id);
    void evict_from_disk_cache(const std::string& source_id);
    size_t memory_cache_size() const;
    size_t disk_cache_count() const;
    uint64_t disk_cache_bytes() const;

    // ── Cache statistics ──
    struct CacheStats {
        uint64_t memory_hits = 0;
        uint64_t memory_misses = 0;
        uint64_t disk_hits = 0;
        uint64_t disk_misses = 0;
        uint64_t generated = 0;
        uint64_t embedded_used = 0;
    };
    CacheStats get_stats() const;
    void reset_stats();

    // ── Utility ──
    static int get_dimension_for_size(ThumbnailSize size);
    static std::string size_to_string(ThumbnailSize size);
    static std::string make_cache_key(const std::string& source_id, ThumbnailSize size);

    // ── Generate from decoded RGB ──
    static void generate_from_rgb(const uint16_t* rgb, int width, int height,
                                   int target_max_dim, ThumbnailResult& result);
    static void generate_from_float_rgb(const float* rgb, int width, int height,
                                         int target_max_dim, ThumbnailResult& result);

    // ── Simple JPEG encoder (minimal, for thumbnail output) ──
    static bool encode_jpeg(const uint8_t* rgb, int width, int height,
                             int quality, std::vector<uint8_t>& jpeg_data);

private:
    ThumbnailOptions default_options_;
    std::string cache_dir_;

    // LRU caches (one per size tier)
    static constexpr size_t kDefaultMemoryCacheSize = 256;
    LruCache<std::string, ThumbnailResult> memory_cache_;
    std::mutex disk_cache_mutex_;

    // Stats
    mutable std::mutex stats_mutex_;
    CacheStats stats_;

    // Internal helpers
    ThumbnailResult generate_internal(const std::string& file_path,
                                       const ThumbnailOptions& options);
    ThumbnailResult generate_from_raw_file(const std::string& file_path,
                                            const ThumbnailOptions& options);
    ThumbnailResult generate_from_image_file(const std::string& file_path,
                                              const ThumbnailOptions& options);
    std::string disk_cache_path(const std::string& source_id, ThumbnailSize size) const;
};

} // namespace alcedo
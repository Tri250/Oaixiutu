#include "thumbnail_decoder.h"
#include "raw_decoder.h"
#include <fstream>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <thread>
#include <chrono>
#include <atomic>
#include <sys/stat.h>
#include <dirent.h>
#include <android/log.h>

#define LOG_TAG "AlcedoThumb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// Utility
// ============================================================

int ThumbnailDecoder::get_dimension_for_size(ThumbnailSize size) {
    switch (size) {
        case ThumbnailSize::SMALL: return 128;
        case ThumbnailSize::MEDIUM: return 256;
        case ThumbnailSize::LARGE: return 512;
        case ThumbnailSize::XLARGE: return 1024;
        case ThumbnailSize::ORIGINAL: return 2048;
        default: return 256;
    }
}

std::string ThumbnailDecoder::size_to_string(ThumbnailSize size) {
    switch (size) {
        case ThumbnailSize::SMALL: return "S";
        case ThumbnailSize::MEDIUM: return "M";
        case ThumbnailSize::LARGE: return "L";
        case ThumbnailSize::XLARGE: return "XL";
        case ThumbnailSize::ORIGINAL: return "ORG";
        default: return "M";
    }
}

std::string ThumbnailDecoder::make_cache_key(const std::string& source_id, ThumbnailSize size) {
    // Simple hash-based key
    std::hash<std::string> hasher;
    size_t h = hasher(source_id);
    char buf[64];
    snprintf(buf, sizeof(buf), "%zu_%s", h, size_to_string(size).c_str());
    return buf;
}

// ============================================================
// Constructor / Destructor
// ============================================================

ThumbnailDecoder::ThumbnailDecoder()
    : memory_cache_(kDefaultMemoryCacheSize) {
    default_options_.max_dimension = 256;
}

ThumbnailDecoder::~ThumbnailDecoder() = default;

void ThumbnailDecoder::set_cache_dir(const std::string& dir) {
    cache_dir_ = dir;
    if (!cache_dir_.empty()) {
        mkdir(cache_dir_.c_str(), 0755);
    }
}

void ThumbnailDecoder::set_memory_cache_size(size_t max_entries) {
    // Create new cache with desired size
    memory_cache_.clear();
}

void ThumbnailDecoder::set_default_options(const ThumbnailOptions& options) {
    default_options_ = options;
}

// ── Cache path helper ──

std::string ThumbnailDecoder::disk_cache_path(const std::string& source_id, ThumbnailSize size) const {
    if (cache_dir_.empty()) return "";
    return cache_dir_ + "/" + make_cache_key(source_id, size) + ".thumb";
}

// ── Disk cache operations ──

bool ThumbnailDecoder::load_from_disk_cache(const std::string& source_id,
                                             ThumbnailSize size, ThumbnailResult& result) {
    std::string path = disk_cache_path(source_id, size);
    if (path.empty()) {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.disk_misses++;
        return false;
    }

    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.disk_misses++;
        return false;
    }

    size_t sz = f.tellg();
    if (sz < 16) {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.disk_misses++;
        return false;
    }

    f.seekg(0);
    // File format: [4 bytes width][4 bytes height][1 byte size_tier][data...]
    int32_t w, h;
    uint8_t tier;
    f.read(reinterpret_cast<char*>(&w), 4);
    f.read(reinterpret_cast<char*>(&h), 4);
    f.read(reinterpret_cast<char*>(&tier), 1);

    result.width = w;
    result.height = h;
    result.size = static_cast<ThumbnailSize>(tier);

    size_t data_sz = sz - 9;
    result.data.resize(data_sz);
    f.read(reinterpret_cast<char*>(result.data.data()), data_sz);
    result.success = true;
    result.is_generated = true;

    {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.disk_hits++;
    }
    LOGI("Disk cache hit: %s (size=%s, %dx%d)", source_id.c_str(),
         size_to_string(size).c_str(), w, h);
    return true;
}

bool ThumbnailDecoder::save_to_disk_cache(const std::string& source_id,
                                           const ThumbnailResult& result) {
    if (cache_dir_.empty() || !result.success) return false;

    std::string path = disk_cache_path(source_id, result.size);
    if (path.empty()) return false;

    std::lock_guard<std::mutex> lock(disk_cache_mutex_);
    std::ofstream f(path, std::ios::binary);
    if (!f) return false;

    int32_t w = result.width;
    int32_t h = result.height;
    uint8_t tier = static_cast<uint8_t>(result.size);

    f.write(reinterpret_cast<const char*>(&w), 4);
    f.write(reinterpret_cast<const char*>(&h), 4);
    f.write(reinterpret_cast<const char*>(&tier), 1);
    f.write(reinterpret_cast<const char*>(result.data.data()), result.data.size());
    f.close();

    return true;
}

// ── Memory cache operations ──

bool ThumbnailDecoder::load_from_memory_cache(const std::string& source_id,
                                               ThumbnailSize size, ThumbnailResult& result) {
    std::string key = make_cache_key(source_id, size);
    bool hit = memory_cache_.get(key, result);
    {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        if (hit) stats_.memory_hits++;
        else stats_.memory_misses++;
    }
    return hit;
}

void ThumbnailDecoder::save_to_memory_cache(const std::string& source_id,
                                             const ThumbnailResult& result) {
    std::string key = make_cache_key(source_id, result.size);
    memory_cache_.put(key, result);
}

// ── Cache management ──

void ThumbnailDecoder::clear_memory_cache() {
    memory_cache_.clear();
}

void ThumbnailDecoder::clear_disk_cache() {
    if (cache_dir_.empty()) return;
    std::lock_guard<std::mutex> lock(disk_cache_mutex_);
    DIR* dir = opendir(cache_dir_.c_str());
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_REG) {
            std::string fname(entry->d_name);
            if (fname.find(".thumb") != std::string::npos) {
                remove((cache_dir_ + "/" + fname).c_str());
            }
        }
    }
    closedir(dir);
}

void ThumbnailDecoder::evict_from_memory_cache(const std::string& source_id) {
    for (int s = 0; s <= 4; ++s) {
        memory_cache_.remove(make_cache_key(source_id, static_cast<ThumbnailSize>(s)));
    }
}

void ThumbnailDecoder::evict_from_disk_cache(const std::string& source_id) {
    if (cache_dir_.empty()) return;
    std::lock_guard<std::mutex> lock(disk_cache_mutex_);
    for (int s = 0; s <= 4; ++s) {
        remove(disk_cache_path(source_id, static_cast<ThumbnailSize>(s)).c_str());
    }
}

size_t ThumbnailDecoder::memory_cache_size() const {
    return memory_cache_.size();
}

size_t ThumbnailDecoder::disk_cache_count() const {
    if (cache_dir_.empty()) return 0;
    size_t count = 0;
    DIR* dir = opendir(cache_dir_.c_str());
    if (!dir) return 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_REG) {
            std::string fname(entry->d_name);
            if (fname.find(".thumb") != std::string::npos) count++;
        }
    }
    closedir(dir);
    return count;
}

uint64_t ThumbnailDecoder::disk_cache_bytes() const {
    if (cache_dir_.empty()) return 0;
    uint64_t total = 0;
    DIR* dir = opendir(cache_dir_.c_str());
    if (!dir) return 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_REG) {
            std::string fname(entry->d_name);
            if (fname.find(".thumb") != std::string::npos) {
                struct stat st;
                if (stat((cache_dir_ + "/" + fname).c_str(), &st) == 0) {
                    total += st.st_size;
                }
            }
        }
    }
    closedir(dir);
    return total;
}

ThumbnailDecoder::CacheStats ThumbnailDecoder::get_stats() const {
    std::lock_guard<std::mutex> lock(stats_mutex_);
    return stats_;
}

void ThumbnailDecoder::reset_stats() {
    std::lock_guard<std::mutex> lock(stats_mutex_);
    stats_ = CacheStats{};
}

// ── Synchronous generation ──

ThumbnailResult ThumbnailDecoder::generate(const std::string& file_path,
                                            const ThumbnailOptions& options) {
    return generate_internal(file_path, options);
}

ThumbnailResult ThumbnailDecoder::generate_from_memory(const uint8_t* data, size_t size,
                                                        const std::string& source_id,
                                                        const ThumbnailOptions& options) {
    ThumbnailResult result;
    result.source_path = source_id;
    result.size = options.size;

    // Check memory cache first
    if (options.use_memory_cache && load_from_memory_cache(source_id, options.size, result)) {
        return result;
    }

    // Check disk cache
    if (options.use_disk_cache && load_from_disk_cache(source_id, options.size, result)) {
        if (options.use_memory_cache) save_to_memory_cache(source_id, result);
        return result;
    }

    // Try extract embedded thumbnail via RawDecoder
    if (options.use_embedded && data && size >= 4) {
        RawDecoder raw;
        std::vector<uint8_t> jpeg;
        int tw = 0, th = 0;
        if (raw.extract_thumbnail_from_memory(data, size, jpeg, tw, th)) {
            result.data = std::move(jpeg);
            result.width = tw;
            result.height = th;
            result.is_embedded = true;
            result.success = true;
            {
                std::lock_guard<std::mutex> lock(stats_mutex_);
                stats_.embedded_used++;
            }
            if (options.use_memory_cache) save_to_memory_cache(source_id, result);
            if (options.use_disk_cache) save_to_disk_cache(source_id, result);
            return result;
        }

        // Try extract preview
        std::vector<uint8_t> preview;
        int pw = 0, ph = 0;
        if (raw.extract_preview_from_memory(data, size, preview, pw, ph)) {
            int target_dim = get_dimension_for_size(options.size);
            result.data = std::move(preview);
            result.width = pw;
            result.height = ph;
            result.is_embedded = true;
            result.success = true;
            {
                std::lock_guard<std::mutex> lock(stats_mutex_);
                stats_.embedded_used++;
            }
            if (options.use_memory_cache) save_to_memory_cache(source_id, result);
            if (options.use_disk_cache) save_to_disk_cache(source_id, result);
            return result;
        }

        // Generate from RAW decode
        if (options.generate_from_raw) {
            RawDecodeOptions raw_opts;
            raw_opts.demosaic = DemosaicMethod::BILINEAR;
            raw_opts.half_resolution = true;
            raw_opts.extract_thumbnail = false;
            raw_opts.extract_preview = false;
            raw_opts.output_float = true;

            RawDecodeResult raw_result;
            if (raw.decode_from_memory(data, size, raw_result, raw_opts)) {
                int target_dim = get_dimension_for_size(options.size);
                result.size = options.size;
                generate_from_float_rgb(raw_result.float_rgb_data.data(),
                                         raw_result.width, raw_result.height,
                                         target_dim, result);
                result.success = true;
                {
                    std::lock_guard<std::mutex> lock(stats_mutex_);
                    stats_.generated++;
                }
                if (options.use_memory_cache) save_to_memory_cache(source_id, result);
                if (options.use_disk_cache) save_to_disk_cache(source_id, result);
                return result;
            }
        }
    }

    result.success = false;
    result.error_message = "Failed to generate thumbnail";
    return result;
}

// ── Async generation ──

void ThumbnailDecoder::generate_async(const std::string& file_path,
                                       ThumbnailCallback callback,
                                       const ThumbnailOptions& options) {
    std::thread([this, file_path, callback, options]() {
        ThumbnailResult result = generate(file_path, options);
        if (callback) callback(result);
    }).detach();
}

void ThumbnailDecoder::generate_async_from_memory(const uint8_t* data, size_t size,
                                                   const std::string& source_id,
                                                   ThumbnailCallback callback,
                                                   const ThumbnailOptions& options) {
    // Copy data for async safety
    std::vector<uint8_t> data_copy(data, data + size);
    std::thread([this, data_copy, source_id, callback, options]() {
        ThumbnailResult result = generate_from_memory(data_copy.data(), data_copy.size(),
                                                       source_id, options);
        if (callback) callback(result);
    }).detach();
}

// ── Batch generation ──

void ThumbnailDecoder::generate_batch(const std::vector<std::string>& file_paths,
                                       ThumbnailCallback per_item_callback,
                                       const ThumbnailOptions& options) {
    std::vector<std::thread> threads;
    for (const auto& path : file_paths) {
        threads.emplace_back([this, path, per_item_callback, options]() {
            ThumbnailResult result = generate(path, options);
            if (per_item_callback) per_item_callback(result);
        });
    }
    for (auto& t : threads) {
        if (t.joinable()) t.join();
    }
}

// ── Internal generation ──

ThumbnailResult ThumbnailDecoder::generate_internal(const std::string& file_path,
                                                     const ThumbnailOptions& options) {
    ThumbnailResult result;
    result.source_path = file_path;
    result.size = options.size;

    // Check memory cache
    if (options.use_memory_cache && load_from_memory_cache(file_path, options.size, result)) {
        return result;
    }

    // Check disk cache
    if (options.use_disk_cache && load_from_disk_cache(file_path, options.size, result)) {
        if (options.use_memory_cache) save_to_memory_cache(file_path, result);
        return result;
    }

    // Detect file type and generate
    std::string fmt = RawDecoder::detect_format(file_path);
    if (RawDecoder::is_raw_format(file_path)) {
        result = generate_from_raw_file(file_path, options);
    } else {
        result = generate_from_image_file(file_path, options);
    }

    // Cache result
    if (result.success) {
        if (options.use_memory_cache) save_to_memory_cache(file_path, result);
        if (options.use_disk_cache) save_to_disk_cache(file_path, result);
    }

    return result;
}

ThumbnailResult ThumbnailDecoder::generate_from_raw_file(const std::string& file_path,
                                                          const ThumbnailOptions& options) {
    ThumbnailResult result;
    result.source_path = file_path;
    result.size = options.size;

    RawDecoder raw;

    // Try embedded thumbnail first
    if (options.use_embedded) {
        std::vector<uint8_t> jpeg;
        int tw = 0, th = 0;
        if (raw.extract_thumbnail(file_path, jpeg, tw, th)) {
            result.data = std::move(jpeg);
            result.width = tw;
            result.height = th;
            result.is_embedded = true;
            result.success = true;
            {
                std::lock_guard<std::mutex> lock(stats_mutex_);
                stats_.embedded_used++;
            }
            return result;
        }

        // Try preview
        std::vector<uint8_t> preview;
        int pw = 0, ph = 0;
        if (raw.extract_preview(file_path, preview, pw, ph)) {
            result.data = std::move(preview);
            result.width = pw;
            result.height = ph;
            result.is_embedded = true;
            result.success = true;
            {
                std::lock_guard<std::mutex> lock(stats_mutex_);
                stats_.embedded_used++;
            }
            return result;
        }
    }

    // Generate from RAW decode
    if (options.generate_from_raw) {
        RawDecodeOptions raw_opts;
        raw_opts.demosaic = DemosaicMethod::BILINEAR;
        raw_opts.half_resolution = true;
        raw_opts.extract_thumbnail = false;
        raw_opts.extract_preview = false;
        raw_opts.output_float = true;

        RawDecodeResult raw_result;
        if (raw.decode(file_path, raw_result, raw_opts)) {
            int target_dim = get_dimension_for_size(options.size);
            generate_from_float_rgb(raw_result.float_rgb_data.data(),
                                     raw_result.width, raw_result.height,
                                     target_dim, result);
            result.success = true;
            {
                std::lock_guard<std::mutex> lock(stats_mutex_);
                stats_.generated++;
            }
            return result;
        }
    }

    result.success = false;
    result.error_message = "Failed to generate thumbnail from RAW";
    return result;
}

ThumbnailResult ThumbnailDecoder::generate_from_image_file(const std::string& file_path,
                                                            const ThumbnailOptions& options) {
    // For non-RAW files, try to read the file and do simple downscaling
    ThumbnailResult result;
    result.source_path = file_path;
    result.size = options.size;
    result.success = false;
    result.error_message = "Non-RAW thumbnail generation not implemented in native layer";
    return result;
}

// ── Generate from decoded RGB ──

void ThumbnailDecoder::generate_from_rgb(const uint16_t* rgb, int width, int height,
                                          int target_max_dim, ThumbnailResult& result) {
    int out_w = width, out_h = height;
    if (width > target_max_dim || height > target_max_dim) {
        float scale = static_cast<float>(target_max_dim) / std::max(width, height);
        out_w = static_cast<int>(width * scale);
        out_h = static_cast<int>(height * scale);
    }
    out_w = std::max(1, out_w);
    out_h = std::max(1, out_h);

    result.data.resize(out_w * out_h * 3);
    result.width = out_w;
    result.height = out_h;

    for (int y = 0; y < out_h; ++y) {
        for (int x = 0; x < out_w; ++x) {
            int sx = (x * width) / out_w;
            int sy = (y * height) / out_h;
            int sidx = (sy * width + sx) * 3;
            int didx = (y * out_w + x) * 3;
            result.data[didx]     = static_cast<uint8_t>(rgb[sidx] >> 8);
            result.data[didx + 1] = static_cast<uint8_t>(rgb[sidx + 1] >> 8);
            result.data[didx + 2] = static_cast<uint8_t>(rgb[sidx + 2] >> 8);
        }
    }
    result.is_generated = true;
}

void ThumbnailDecoder::generate_from_float_rgb(const float* rgb, int width, int height,
                                                int target_max_dim, ThumbnailResult& result) {
    int out_w = width, out_h = height;
    if (width > target_max_dim || height > target_max_dim) {
        float scale = static_cast<float>(target_max_dim) / std::max(width, height);
        out_w = static_cast<int>(width * scale);
        out_h = static_cast<int>(height * scale);
    }
    out_w = std::max(1, out_w);
    out_h = std::max(1, out_h);

    result.data.resize(out_w * out_h * 3);
    result.width = out_w;
    result.height = out_h;

    for (int y = 0; y < out_h; ++y) {
        for (int x = 0; x < out_w; ++x) {
            int sx = (x * width) / out_w;
            int sy = (y * height) / out_h;
            int sidx = (sy * width + sx) * 3;
            int didx = (y * out_w + x) * 3;
            auto clamp_byte = [](float v) -> uint8_t {
                int iv = static_cast<int>(v * 255.0f);
                return static_cast<uint8_t>(iv < 0 ? 0 : (iv > 255 ? 255 : iv));
            };
            result.data[didx]     = clamp_byte(rgb[sidx]);
            result.data[didx + 1] = clamp_byte(rgb[sidx + 1]);
            result.data[didx + 2] = clamp_byte(rgb[sidx + 2]);
        }
    }
    result.is_generated = true;
}

// ── Simple JPEG encoder (minimal) ──

bool ThumbnailDecoder::encode_jpeg(const uint8_t* rgb, int width, int height,
                                    int quality, std::vector<uint8_t>& jpeg_data) {
    // Minimal JPEG encoding is complex; for a full implementation we'd need
    // a complete JPEG encoder (DCT, quantization, Huffman coding).
    // Storing raw RGB data as fallback.
    jpeg_data.assign(rgb, rgb + width * height * 3);
    LOGW("encode_jpeg: storing raw RGB (JPEG encoder not implemented)");
    return false;
}

} // namespace alcedo
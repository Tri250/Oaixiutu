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

// ============================================================
// Non-RAW thumbnail helpers (file reading, EXIF, JPEG decode)
// ============================================================
namespace {

// Read an entire file into a byte vector.
bool read_file_bytes(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    f.seekg(0, std::ios::end);
    std::streamoff sz = f.tellg();
    if (sz < 0) return false;
    f.seekg(0, std::ios::beg);
    out.resize(static_cast<size_t>(sz));
    if (!out.empty() && !f.read(reinterpret_cast<char*>(out.data()), out.size()))
        return false;
    return true;
}

// JPEG zigzag scan order (Annex A of ITU-T T.81).
static const int kJpegZigzag[64] = {
     0,  1,  8, 16,  9,  2,  3, 10,
    17, 24, 32, 25, 18, 11,  4,  5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13,  6,  7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63
};

// Precomputed cosine basis for 8-point IDCT: idctCos[k][n] = cos((2n+1)*k*pi/16).
static float idctCos(int k, int n) {
    return std::cos(M_PI * (2 * n + 1) * k / 16.0);
}

// 8x8 inverse DCT on a dequantized block (in-place: int coeffs -> float samples).
void idct8x8(const int coeffs[64], float out[64]) {
    float row[64];
    const float invSqrt2 = 1.0f / std::sqrt(2.0f);
    // 1D IDCT on rows
    for (int y = 0; y < 8; ++y) {
        for (int x = 0; x < 8; ++x) {
            float sum = 0.0f;
            for (int u = 0; u < 8; ++u) {
                float cu = (u == 0) ? invSqrt2 : 1.0f;
                sum += cu * coeffs[y * 8 + u] * idctCos(u, x);
            }
            row[y * 8 + x] = sum * 0.5f;
        }
    }
    // 1D IDCT on columns
    for (int x = 0; x < 8; ++x) {
        for (int y = 0; y < 8; ++y) {
            float sum = 0.0f;
            for (int v = 0; v < 8; ++v) {
                float cv = (v == 0) ? invSqrt2 : 1.0f;
                sum += cv * row[v * 8 + x] * idctCos(v, y);
            }
            out[y * 8 + x] = sum * 0.5f;
        }
    }
}

// JPEG Huffman table (baseline).
struct JpegHuff {
    uint8_t lengths[16] = {0};
    std::vector<uint8_t> symbols;
    int minCode[17] = {0};
    int maxCode[17] = {-1};
    int valPtr[17] = {0};
    bool valid = false;
};

void build_jpeg_huff(JpegHuff& t) {
    int k = 0;
    int code = 0;
    for (int i = 0; i < 16; ++i) {
        t.valPtr[i] = k;
        t.minCode[i] = code;
        code += t.lengths[i];
        t.maxCode[i] = (t.lengths[i] == 0) ? -1 : code - 1;
        k += t.lengths[i];
        code <<= 1;
    }
    t.valid = (k > 0);
}

// Compact baseline JPEG decoder (SOF0). Supports YCbCr 4:4:4/4:2:2/4:2:0 and
// grayscale. Sufficient for embedded EXIF thumbnails and small JPEGs.
struct BaselineJpeg {
    const uint8_t* data = nullptr;
    size_t size = 0;
    int width = 0, height = 0;
    int numComp = 0;
    int precision = 8;

    struct Comp {
        int id = 0, hSamp = 1, vSamp = 1;
        int qtId = 0, dcId = 0, acId = 0;
        int blocksX = 0, blocksY = 0;
    };
    Comp comps[3];
    int maxH = 1, maxV = 1;

    uint8_t qtables[4][64];
    JpegHuff dcTabs[4];
    JpegHuff acTabs[4];

    int prevDc[3] = {0, 0, 0};

    // De-stuffed scan bit stream.
    std::vector<uint8_t> scan;
    size_t bitPos = 0;

    bool decode(const uint8_t* d, size_t sz, std::vector<uint8_t>& rgb);

    int readBit() {
        size_t bytePos = bitPos >> 3;
        if (bytePos >= scan.size()) return 0;
        int b = (scan[bytePos] >> (7 - (bitPos & 7))) & 1;
        ++bitPos;
        return b;
    }
    int readBits(int n) {
        int v = 0;
        for (int i = 0; i < n; ++i) v = (v << 1) | readBit();
        return v;
    }
    int decodeSymbol(const JpegHuff& t) {
        if (!t.valid) return -1;
        int code = 0;
        for (int len = 1; len <= 16; ++len) {
            code = (code << 1) | readBit();
            if (code <= t.maxCode[len - 1]) {
                int idx = t.valPtr[len - 1] + (code - t.minCode[len - 1]);
                if (idx < 0 || idx >= static_cast<int>(t.symbols.size())) return -1;
                return t.symbols[idx];
            }
        }
        return -1;
    }
    int extend(int v, int ssss) {
        if (ssss == 0) return 0;
        int vt = 1 << (ssss - 1);
        return (v < vt) ? (v + 1 - (1 << ssss)) : v;
    }
    bool decodeBlock(int compIdx, int block[64]) {
        Comp& c = comps[compIdx];
        // DC
        int s = decodeSymbol(dcTabs[c.dcId]);
        if (s < 0) return false;
        int dc = extend(readBits(s), s);
        prevDc[compIdx] += dc;
        block[0] = prevDc[compIdx];
        // AC
        for (int k = 1; k < 64; ) {
            int rs = decodeSymbol(acTabs[c.acId]);
            if (rs < 0) return false;
            int run = (rs >> 4) & 0x0F;
            int size = rs & 0x0F;
            if (size == 0) {
                if (run == 15) { k += 16; continue; }
                break;  // EOB
            }
            k += run;
            if (k >= 64) break;
            block[kJpegZigzag[k]] = extend(readBits(size), size);
            ++k;
        }
        // Dequantize
        for (int k = 0; k < 64; ++k) block[k] *= qtables[c.qtId][k];
        return true;
    }
};

bool BaselineJpeg::decode(const uint8_t* d, size_t sz, std::vector<uint8_t>& rgb) {
    data = d;
    size = sz;
    if (size < 2 || data[0] != 0xFF || data[1] != 0xD8) return false;

    size_t pos = 2;
    size_t scanStart = 0;
    bool haveSos = false;

    while (pos + 1 < size) {
        if (data[pos] != 0xFF) { ++pos; continue; }
        while (pos + 1 < size && data[pos + 1] == 0xFF) ++pos;
        if (pos + 1 >= size) break;
        uint8_t marker = data[pos + 1];
        if (marker == 0xD9) break;
        if (marker == 0xDA) {
            if (pos + 4 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t sosEnd = pos + 2 + segLen;
            if (sosEnd > size) break;
            uint8_t ns = data[pos + 4];
            numComp = ns;
            for (int i = 0; i < ns && i < 3; ++i) {
                size_t base = pos + 5 + i * 2;
                if (base + 1 >= size) break;
                comps[i].id = data[base];
                uint8_t tdTa = data[base + 1];
                comps[i].dcId = (tdTa >> 4) & 0x0F;
                comps[i].acId = tdTa & 0x0F;
            }
            scanStart = sosEnd;
            haveSos = true;
            break;
        }
        if (marker == 0xC0) {  // SOF0 — baseline
            if (pos + 8 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            precision = data[pos + 4];
            height = (data[pos + 5] << 8) | data[pos + 6];
            width = (data[pos + 7] << 8) | data[pos + 8];
            numComp = data[pos + 9];
            size_t p = pos + 10;
            for (int i = 0; i < numComp && i < 3; ++i) {
                if (p + 2 >= size) break;
                comps[i].id = data[p++];
                comps[i].hSamp = (data[p] >> 4) & 0x0F;
                comps[i].vSamp = data[p] & 0x0F;
                ++p;
                comps[i].qtId = data[p++];
            }
            maxH = comps[0].hSamp; maxV = comps[0].vSamp;
            for (int i = 1; i < numComp; ++i) {
                if (comps[i].hSamp > maxH) maxH = comps[i].hSamp;
                if (comps[i].vSamp > maxV) maxV = comps[i].vSamp;
            }
            pos += 2 + segLen;
            continue;
        }
        if (marker == 0xDB) {  // DQT
            if (pos + 4 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t dqtEnd = pos + 2 + segLen;
            if (dqtEnd > size) dqtEnd = size;
            size_t p = pos + 4;
            while (p < dqtEnd) {
                uint8_t info = data[p++];
                int qPrec = (info >> 4) & 0x0F;
                int tid = info & 0x0F;
                if (tid < 0 || tid > 3) break;
                if (qPrec == 0) {
                    if (p + 64 > dqtEnd) break;
                    for (int i = 0; i < 64; ++i) qtables[tid][i] = data[p + i];
                    p += 64;
                } else {
                    if (p + 128 > dqtEnd) break;
                    for (int i = 0; i < 64; ++i)
                        qtables[tid][i] = (data[p + i * 2] << 8) | data[p + i * 2 + 1];
                    p += 128;
                }
            }
            pos = dqtEnd;
            continue;
        }
        if (marker == 0xC4) {  // DHT
            if (pos + 4 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t dhtEnd = pos + 2 + segLen;
            if (dhtEnd > size) dhtEnd = size;
            size_t p = pos + 4;
            while (p < dhtEnd) {
                uint8_t info = data[p++];
                int cls = (info >> 4) & 0x0F;
                int tid = info & 0x0F;
                if (p + 16 > dhtEnd) break;
                JpegHuff* t = (cls == 0) ? &dcTabs[tid] : &acTabs[tid];
                int total = 0;
                for (int i = 0; i < 16; ++i) { t->lengths[i] = data[p + i]; total += t->lengths[i]; }
                p += 16;
                if (p + total > dhtEnd) break;
                t->symbols.assign(data + p, data + p + total);
                p += total;
                build_jpeg_huff(*t);
            }
            pos = dhtEnd;
            continue;
        }
        if (marker == 0x00 || marker == 0xFF || (marker >= 0xD0 && marker <= 0xD7)) {
            pos += 2;
            continue;
        }
        if (pos + 4 > size) break;
        uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
        pos += 2 + segLen;
    }

    if (!haveSos || numComp == 0 || width == 0 || height == 0) return false;

    // De-stuff the scan data.
    scan.clear();
    scan.reserve(size - scanStart);
    for (size_t i = scanStart; i < size; ++i) {
        uint8_t b = data[i];
        if (b == 0xFF && i + 1 < size) {
            uint8_t next = data[i + 1];
            if (next == 0x00) { ++i; scan.push_back(0xFF); continue; }
            if (next >= 0xD0 && next <= 0xD7) { ++i; continue; }
            break;
        }
        scan.push_back(b);
    }
    bitPos = 0;
    for (int i = 0; i < numComp; ++i) prevDc[i] = 0;

    const int mcuW = (width + maxH * 8 - 1) / (maxH * 8);
    const int mcuH = (height + maxV * 8 - 1) / (maxV * 8);
    for (int i = 0; i < numComp; ++i) {
        comps[i].blocksX = mcuW * comps[i].hSamp;
        comps[i].blocksY = mcuH * comps[i].vSamp;
    }

    // Per-component full-resolution sample planes.
    std::vector<std::vector<int16_t>> planes(numComp);
    for (int i = 0; i < numComp; ++i) {
        planes[i].assign(static_cast<size_t>(comps[i].blocksX) * 8 *
                         comps[i].blocksY * 8, 0);
    }

    for (int my = 0; my < mcuH; ++my) {
        for (int mx = 0; mx < mcuW; ++mx) {
            for (int ci = 0; ci < numComp; ++ci) {
                Comp& c = comps[ci];
                for (int by = 0; by < c.vSamp; ++by) {
                    for (int bx = 0; bx < c.hSamp; ++bx) {
                        int blockX = mx * c.hSamp + bx;
                        int blockY = my * c.vSamp + by;
                        int coeffs[64] = {0};
                        if (!decodeBlock(ci, coeffs)) {
                            // Continue with whatever was decoded so far.
                        }
                        float samples[64];
                        idct8x8(coeffs, samples);
                        int planeW = c.blocksX * 8;
                        for (int py = 0; py < 8; ++py) {
                            for (int px = 0; px < 8; ++px) {
                                int v = static_cast<int>(std::round(samples[py * 8 + px])) + 128;
                                if (v < 0) v = 0;
                                if (v > 255) v = 255;
                                size_t idx = static_cast<size_t>(blockY * 8 + py) * planeW +
                                             (blockX * 8 + px);
                                planes[ci][idx] = static_cast<int16_t>(v);
                            }
                        }
                    }
                }
            }
        }
    }

    rgb.assign(static_cast<size_t>(width) * height * 3, 0);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int r, g, b;
            if (numComp == 1) {
                int sx = x * comps[0].hSamp / maxH;
                int sy = y * comps[0].vSamp / maxV;
                int planeW = comps[0].blocksX * 8;
                int Y = planes[0][sy * planeW + sx];
                r = g = b = Y;
            } else {
                int ySx = x * comps[0].hSamp / maxH;
                int ySy = y * comps[0].vSamp / maxV;
                int cbSx = x * comps[1].hSamp / maxH;
                int cbSy = y * comps[1].vSamp / maxV;
                int crSx = x * comps[2].hSamp / maxH;
                int crSy = y * comps[2].vSamp / maxV;
                int Yp = planes[0][ySy * (comps[0].blocksX * 8) + ySx];
                int Cb = planes[1][cbSy * (comps[1].blocksX * 8) + cbSx] - 128;
                int Cr = planes[2][crSy * (comps[2].blocksX * 8) + crSx] - 128;
                r = Yp + static_cast<int>(1.402f * Cr);
                g = Yp - static_cast<int>(0.344f * Cb + 0.714f * Cr);
                b = Yp + static_cast<int>(1.772f * Cb);
            }
            auto cl = [](int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); };
            size_t idx = (static_cast<size_t>(y) * width + x) * 3;
            rgb[idx]     = static_cast<uint8_t>(cl(r));
            rgb[idx + 1] = static_cast<uint8_t>(cl(g));
            rgb[idx + 2] = static_cast<uint8_t>(cl(b));
        }
    }
    return true;
}

// Extract the embedded EXIF thumbnail (JPEG bytes) from APP1 of a JPEG file.
bool extract_exif_thumbnail(const uint8_t* data, size_t size,
                             std::vector<uint8_t>& thumb) {
    if (size < 4 || data[0] != 0xFF || data[1] != 0xD8) return false;
    size_t pos = 2;
    while (pos + 1 < size) {
        if (data[pos] != 0xFF) { ++pos; continue; }
        uint8_t marker = data[pos + 1];
        if (marker == 0xE1 && pos + 4 < size) {  // APP1 (EXIF)
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            if (pos + 2 + segLen > size) return false;
            size_t p = pos + 4;
            if (p + 6 > size) return false;
            // Check "Exif\0\0" header
            if (data[p] == 'E' && data[p + 1] == 'x' && data[p + 2] == 'i' &&
                data[p + 3] == 'f' && data[p + 4] == 0 && data[p + 5] == 0) {
                p += 6;
                size_t tiffStart = p;
                if (p + 8 > size) return false;
                bool le = (data[p] == 'I' && data[p + 1] == 'I');
                bool be = (data[p] == 'M' && data[p + 1] == 'M');
                if (!le && !be) return false;
                auto r16 = [&](size_t o) -> uint16_t {
                    if (le) return data[tiffStart + o] | (data[tiffStart + o + 1] << 8);
                    return (data[tiffStart + o] << 8) | data[tiffStart + o + 1];
                };
                auto r32 = [&](size_t o) -> uint32_t {
                    if (le) return data[tiffStart + o] | (data[tiffStart + o + 1] << 8) |
                                   (data[tiffStart + o + 2] << 16) | (data[tiffStart + o + 3] << 24);
                    return (data[tiffStart + o] << 24) | (data[tiffStart + o + 1] << 16) |
                           (data[tiffStart + o + 2] << 8) | data[tiffStart + o + 3];
                };
                uint32_t ifd0 = r32(4);
                size_t ifd0Abs = tiffStart + ifd0;
                if (ifd0Abs + 2 > size) return false;
                uint16_t entries = r16(ifd0);
                size_t entryBase = ifd0Abs + 2;
                for (uint16_t i = 0; i < entries; ++i) {
                    size_t e = entryBase + i * 12;
                    if (e + 12 > size) break;
                    uint16_t tag = r16(e - tiffStart);
                    if (tag == 0x0201) {        // JPEGInterchangeFormat (thumbnail offset)
                        uint32_t off = r32(e - tiffStart + 8);
                        // The companion length tag (0x0202) must also be present.
                        for (uint16_t j = 0; j < entries; ++j) {
                            size_t e2 = entryBase + j * 12;
                            if (e2 + 12 > size) break;
                            if (r16(e2 - tiffStart) == 0x0202) {
                                uint32_t len = r32(e2 - tiffStart + 8);
                                if (off + len > size) return false;
                                thumb.assign(data + tiffStart + off,
                                             data + tiffStart + off + len);
                                return !thumb.empty();
                            }
                        }
                    }
                    if (tag == 0x8769) {        // Exif IFD pointer — recurse not needed for thumb
                        // Embedded thumbnail lives in IFD0 in practice; skip.
                    }
                }
            }
            pos += 2 + segLen;
            continue;
        }
        if (marker == 0xDA || marker == 0xD9) break;  // SOS or EOI: no more APP1
        if (marker == 0x00 || marker == 0xFF) { pos += 2; continue; }
        if (pos + 4 > size) break;
        uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
        pos += 2 + segLen;
    }
    return false;
}

// Parse JPEG SOF0 marker to obtain image dimensions.
bool parse_jpeg_size(const uint8_t* data, size_t size, int& w, int& h) {
    if (size < 4 || data[0] != 0xFF || data[1] != 0xD8) return false;
    size_t pos = 2;
    while (pos + 1 < size) {
        if (data[pos] != 0xFF) { ++pos; continue; }
        uint8_t marker = data[pos + 1];
        if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
            if (pos + 9 > size) return false;
            h = (data[pos + 5] << 8) | data[pos + 6];
            w = (data[pos + 7] << 8) | data[pos + 8];
            return true;
        }
        if (marker == 0xDA) break;
        if (marker == 0x00 || marker == 0xFF || (marker >= 0xD0 && marker <= 0xD7)) {
            pos += 2; continue;
        }
        if (pos + 4 > size) break;
        uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
        pos += 2 + segLen;
    }
    return false;
}

// Box-average downsample of an RGB888 buffer by an integer factor.
void downsample_rgb(const uint8_t* src, int sw, int sh,
                    int factor, std::vector<uint8_t>& dst, int& dw, int& dh) {
    if (factor < 1) factor = 1;
    dw = std::max(1, sw / factor);
    dh = std::max(1, sh / factor);
    dst.assign(static_cast<size_t>(dw) * dh * 3, 0);
    for (int y = 0; y < dh; ++y) {
        for (int x = 0; x < dw; ++x) {
            int r = 0, g = 0, b = 0, count = 0;
            for (int fy = 0; fy < factor; ++fy) {
                for (int fx = 0; fx < factor; ++fx) {
                    int sx = x * factor + fx;
                    int sy = y * factor + fy;
                    if (sx >= sw || sy >= sh) continue;
                    size_t sidx = (static_cast<size_t>(sy) * sw + sx) * 3;
                    r += src[sidx];
                    g += src[sidx + 1];
                    b += src[sidx + 2];
                    ++count;
                }
            }
            if (count == 0) count = 1;
            size_t didx = (static_cast<size_t>(y) * dw + x) * 3;
            dst[didx]     = static_cast<uint8_t>(r / count);
            dst[didx + 1] = static_cast<uint8_t>(g / count);
            dst[didx + 2] = static_cast<uint8_t>(b / count);
        }
    }
}

// ============================================================
// Baseline JPEG encoder (DCT + quantization + Huffman)
// ============================================================

// Standard luminance quantization table (ITU-T T.81 Annex K, natural order).
static const int kStdLumQuant[64] = {
    16, 11, 10, 16, 24, 40, 51, 61,
    12, 12, 14, 19, 26, 58, 60, 55,
    14, 13, 16, 24, 40, 57, 69, 56,
    14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68,109,103, 77,
    24, 35, 55, 64, 81,104,113, 92,
    49, 64, 78, 87,103,121,120,101,
    72, 92, 95, 98,112,100,103, 99
};

// Standard chrominance quantization table (natural order).
static const int kStdChrQuant[64] = {
    17, 18, 24, 47, 99, 99, 99, 99,
    18, 21, 26, 66, 99, 99, 99, 99,
    24, 26, 56, 99, 99, 99, 99, 99,
    47, 66, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99
};

// Standard DC luminance Huffman table (ITU-T T.81 Annex K).
static const uint8_t kDcLumBits[16] = {
    0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0
};
static const uint8_t kDcLumVals[12] = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
};

// Standard AC luminance Huffman table.
static const uint8_t kAcLumBits[16] = {
    0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d
};
static const uint8_t kAcLumVals[162] = {
    0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
    0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
    0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
    0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
    0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
    0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
    0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
    0x8a, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99,
    0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8,
    0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7,
    0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6,
    0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5,
    0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2, 0xe3,
    0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1,
    0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9,
    0xfa
};

// MSB-first bit writer with JPEG byte stuffing (0xFF -> 0xFF 0x00).
struct BitWriter {
    std::vector<uint8_t> data;
    uint32_t acc = 0;
    int bits = 0;
    void writeBits(uint32_t code, int len) {
        for (int i = len - 1; i >= 0; --i) {
            acc = (acc << 1) | ((code >> i) & 1u);
            if (++bits == 8) flushByte();
        }
    }
    void flushByte() {
        uint8_t b = acc & 0xFF;
        data.push_back(b);
        if (b == 0xFF) data.push_back(0x00);
        acc = 0;
        bits = 0;
    }
    void flush() {
        if (bits > 0) {
            acc <<= (8 - bits);
            flushByte();
        }
    }
};

// Encoding lookup entry: symbol -> (code, length).
struct EncHuff {
    uint16_t code = 0;
    uint8_t length = 0;
    bool valid = false;
};

void build_enc_table(const uint8_t bits[16], const uint8_t values[], int count,
                     EncHuff table[256]) {
    int code = 0;
    int k = 0;
    for (int len = 1; len <= 16; ++len) {
        for (int i = 0; i < bits[len - 1]; ++i) {
            if (k >= count) break;
            table[values[k]].code = static_cast<uint16_t>(code);
            table[values[k]].length = static_cast<uint8_t>(len);
            table[values[k]].valid = true;
            ++code;
            ++k;
        }
        code <<= 1;
    }
}

// Forward 8x8 DCT (separable). Reuses idctCos() since cos((2n+1)*k*pi/16) is
// symmetric in the role of (k, n) for the forward/inverse transforms.
void forwardDct(const int16_t block[64], float out[64]) {
    float row[64];
    const float invSqrt2 = 1.0f / std::sqrt(2.0f);
    // Row transform: G(y,u) = (C(u)/2) * sum_x block[y][x] * cos((2x+1)*u*pi/16)
    for (int y = 0; y < 8; ++y) {
        for (int u = 0; u < 8; ++u) {
            float sum = 0.0f;
            for (int x = 0; x < 8; ++x) {
                sum += block[y * 8 + x] * idctCos(u, x);
            }
            float cu = (u == 0) ? invSqrt2 : 1.0f;
            row[y * 8 + u] = sum * cu * 0.5f;
        }
    }
    // Column transform: F(v,u) = (C(v)/2) * sum_y row[y][u] * cos((2y+1)*v*pi/16)
    for (int v = 0; v < 8; ++v) {
        for (int u = 0; u < 8; ++u) {
            float sum = 0.0f;
            for (int y = 0; y < 8; ++y) {
                sum += row[y * 8 + u] * idctCos(v, y);
            }
            float cv = (v == 0) ? invSqrt2 : 1.0f;
            out[v * 8 + u] = sum * cv * 0.5f;
        }
    }
}

// Write a JPEG marker with payload (length field is auto-computed).
void writeMarker(std::vector<uint8_t>& out, uint8_t marker,
                 const uint8_t* payload, size_t payloadLen) {
    out.push_back(0xFF);
    out.push_back(marker);
    uint16_t len = static_cast<uint16_t>(payloadLen + 2);
    out.push_back((len >> 8) & 0xFF);
    out.push_back(len & 0xFF);
    out.insert(out.end(), payload, payload + payloadLen);
}

// Encode one 8x8 block (DC differential + AC run-length, both Huffman coded).
void encodeBlock(BitWriter& w, const int coeffs[64], int& prevDc,
                 const EncHuff dcTab[256], const EncHuff acTab[256]) {
    // DC coefficient (differential).
    int dc = coeffs[0];
    int diff = dc - prevDc;
    prevDc = dc;
    int ssss = 0;
    int mag = diff;
    if (diff < 0) mag = -diff - 1;
    while (mag > 0) { ++ssss; mag >>= 1; }
    const EncHuff& dcE = dcTab[ssss];
    w.writeBits(dcE.code, dcE.length);
    if (ssss > 0) {
        int v = (diff < 0) ? (diff + (1 << ssss) - 1) : diff;
        w.writeBits(static_cast<uint32_t>(v), ssss);
    }

    // AC coefficients in zigzag order, with run-length encoding.
    int run = 0;
    for (int i = 1; i < 64; ++i) {
        int coeff = coeffs[kJpegZigzag[i]];
        if (coeff == 0) {
            ++run;
        } else {
            while (run >= 16) {
                const EncHuff& e = acTab[0xF0];  // ZRL: 16 zeros
                w.writeBits(e.code, e.length);
                run -= 16;
            }
            int absCoeff = (coeff < 0) ? -coeff : coeff;
            int s = 0;
            while (absCoeff > 0) { ++s; absCoeff >>= 1; }
            uint8_t rs = static_cast<uint8_t>((run << 4) | s);
            const EncHuff& e = acTab[rs];
            w.writeBits(e.code, e.length);
            int v = (coeff < 0) ? (coeff + (1 << s) - 1) : coeff;
            w.writeBits(static_cast<uint32_t>(v), s);
            run = 0;
        }
    }
    if (run > 0) {
        const EncHuff& e = acTab[0x00];  // End of Block
        w.writeBits(e.code, e.length);
    }
}

// Encode an RGB888 image as a baseline JPEG (YCbCr 4:4:4).
bool encodeBaselineJpeg(const uint8_t* rgb, int width, int height,
                         int quality, std::vector<uint8_t>& output) {
    if (!rgb || width <= 0 || height <= 0) return false;
    quality = std::max(1, std::min(100, quality));

    // Quality-scaled quantization tables (natural order).
    int qScale = (quality < 50) ? (5000 / quality) : (200 - 2 * quality);
    auto scaleQ = [&](int v) -> int {
        int s = (v * qScale + 50) / 100;
        return std::max(1, std::min(255, s));
    };
    uint8_t lumQ[64], chrQ[64];
    for (int i = 0; i < 64; ++i) {
        lumQ[i] = static_cast<uint8_t>(scaleQ(kStdLumQuant[i]));
        chrQ[i] = static_cast<uint8_t>(scaleQ(kStdChrQuant[i]));
    }

    // Build Huffman encoding tables (luminance tables used for all components).
    EncHuff dcTab[256], acTab[256];
    build_enc_table(kDcLumBits, kDcLumVals, 12, dcTab);
    build_enc_table(kAcLumBits, kAcLumVals, 162, acTab);

    output.clear();
    // SOI
    output.push_back(0xFF); output.push_back(0xD8);

    // DQT — luminance (id 0) and chrominance (id 1), zigzag order.
    {
        uint8_t payload[2 + 64 + 1 + 64];
        payload[0] = 0x00;  // precision 0 (8-bit), table id 0
        for (int i = 0; i < 64; ++i) payload[1 + i] = lumQ[kJpegZigzag[i]];
        payload[1 + 64] = 0x01;  // precision 0, table id 1
        for (int i = 0; i < 64; ++i) payload[2 + 64 + i] = chrQ[kJpegZigzag[i]];
        writeMarker(output, 0xDB, payload, sizeof(payload));
    }

    // SOF0 — baseline, 3 components, 4:4:4 (sampling 1x1 each).
    {
        uint8_t payload[15];
        payload[0] = 8;     // precision (8 bits)
        payload[1] = (height >> 8) & 0xFF;
        payload[2] = height & 0xFF;
        payload[3] = (width >> 8) & 0xFF;
        payload[4] = width & 0xFF;
        payload[5] = 3;     // 3 components
        payload[6] = 1;  payload[7] = 0x11;  payload[8]  = 0;  // Y  -> qt 0, sampling 1x1
        payload[9] = 2;  payload[10] = 0x11; payload[11] = 1;  // Cb -> qt 1, sampling 1x1
        payload[12] = 3; payload[13] = 0x11; payload[14] = 1;  // Cr -> qt 1, sampling 1x1
        writeMarker(output, 0xC0, payload, 15);
    }

    // DHT — DC luminance (class 0, id 0) and AC luminance (class 1, id 0).
    {
        std::vector<uint8_t> payload;
        payload.push_back(0x00);  // class 0 (DC), id 0
        payload.insert(payload.end(), kDcLumBits, kDcLumBits + 16);
        payload.insert(payload.end(), kDcLumVals, kDcLumVals + 12);
        writeMarker(output, 0xC4, payload.data(), payload.size());
    }
    {
        std::vector<uint8_t> payload;
        payload.push_back(0x10);  // class 1 (AC), id 0
        payload.insert(payload.end(), kAcLumBits, kAcLumBits + 16);
        payload.insert(payload.end(), kAcLumVals, kAcLumVals + 162);
        writeMarker(output, 0xC4, payload.data(), payload.size());
    }

    // SOS — 3 components, all using DC/AC table 0.
    {
        uint8_t payload[12];
        payload[0] = 3;   // 3 components
        payload[1] = 1;  payload[2] = 0x00;  // Y:  DC/AC table 0
        payload[3] = 2;  payload[4] = 0x00;  // Cb: DC/AC table 0
        payload[5] = 3;  payload[6] = 0x00;  // Cr: DC/AC table 0
        payload[7] = 0;  // Ss
        payload[8] = 63; // Se
        payload[9] = 0;  // Ah/Al
        writeMarker(output, 0xDA, payload, 10);
    }

    // Scan data: 4:4:4, MCU = 1 block per component, 8x8 pixels.
    const int mcuW = (width + 7) / 8;
    const int mcuH = (height + 7) / 8;
    BitWriter w;
    int prevDcY = 0, prevDcCb = 0, prevDcCr = 0;
    for (int my = 0; my < mcuH; ++my) {
        for (int mx = 0; mx < mcuW; ++mx) {
            for (int ci = 0; ci < 3; ++ci) {
                int16_t block[64];
                for (int by = 0; by < 8; ++by) {
                    for (int bx = 0; bx < 8; ++bx) {
                        int px = std::min(mx * 8 + bx, width - 1);
                        int py = std::min(my * 8 + by, height - 1);
                        size_t idx = (static_cast<size_t>(py) * width + px) * 3;
                        int R = rgb[idx], G = rgb[idx + 1], B = rgb[idx + 2];
                        int sample;
                        if (ci == 0) {
                            sample = static_cast<int>(0.299f * R + 0.587f * G + 0.114f * B) - 128;
                        } else if (ci == 1) {
                            sample = static_cast<int>(-0.168736f * R - 0.331264f * G + 0.5f * B) + 128 - 128;
                        } else {
                            sample = static_cast<int>(0.5f * R - 0.418688f * G - 0.081312f * B) + 128 - 128;
                        }
                        block[by * 8 + bx] = static_cast<int16_t>(sample);
                    }
                }
                float dct[64];
                forwardDct(block, dct);
                int coeffs[64];
                const uint8_t* qt = (ci == 0) ? lumQ : chrQ;
                for (int i = 0; i < 64; ++i) {
                    coeffs[i] = static_cast<int>(std::round(dct[i] / qt[i]));
                }
                int& prevDc = (ci == 0) ? prevDcY : (ci == 1) ? prevDcCb : prevDcCr;
                encodeBlock(w, coeffs, prevDc, dcTab, acTab);
            }
        }
    }
    w.flush();
    output.insert(output.end(), w.data.begin(), w.data.end());

    // EOI
    output.push_back(0xFF);
    output.push_back(0xD9);
    return true;
}

} // namespace

ThumbnailResult ThumbnailDecoder::generate_from_image_file(const std::string& file_path,
                                                            const ThumbnailOptions& options) {
    // Non-RAW thumbnail generation:
    // 1. Try the embedded EXIF thumbnail (small JPEG).
    // 2. Otherwise, downsampled decode of the full image.
    ThumbnailResult result;
    result.source_path = file_path;
    result.size = options.size;
    result.success = false;

    int targetSize = get_dimension_for_size(options.size);
    std::vector<uint8_t> fileData;
    if (!read_file_bytes(file_path, fileData)) {
        result.error_message = "Failed to read image file";
        return result;
    }

    // 1. Try the embedded EXIF thumbnail first.
    if (options.use_embedded) {
        std::vector<uint8_t> exifThumb;
        if (extract_exif_thumbnail(fileData.data(), fileData.size(), exifThumb) &&
            !exifThumb.empty()) {
            std::vector<uint8_t> rgb;
            int tw = 0, th = 0;
            BaselineJpeg dec;
            if (dec.decode(exifThumb.data(), exifThumb.size(), rgb)) {
                tw = dec.width; th = dec.height;
                // If the embedded thumbnail is already small enough, emit it directly;
                // otherwise downsample to the requested size.
                if (tw <= targetSize && th <= targetSize) {
                    result.data = std::move(rgb);
                    result.width = tw;
                    result.height = th;
                } else {
                    int factor = 1;
                    while (tw / (factor * 2) >= targetSize && th / (factor * 2) >= targetSize) {
                        factor *= 2;
                    }
                    std::vector<uint8_t> small;
                    int sw, sh;
                    downsample_rgb(rgb.data(), tw, th, factor, small, sw, sh);
                    result.data = std::move(small);
                    result.width = sw;
                    result.height = sh;
                }
                result.is_embedded = true;
                result.is_generated = true;
                result.success = true;
                {
                    std::lock_guard<std::mutex> lock(stats_mutex_);
                    stats_.embedded_used++;
                }
                return result;
            }
        }
    }

    // 2. Downsampled decode of the full image.
    int origW = 0, origH = 0;
    if (!parse_jpeg_size(fileData.data(), fileData.size(), origW, origH)) {
        result.error_message = "Unsupported or invalid JPEG (no SOF marker)";
        return result;
    }

    int sampleSize = 1;
    while (origW / (sampleSize * 2) >= targetSize && origH / (sampleSize * 2) >= targetSize) {
        sampleSize *= 2;
    }

    std::vector<uint8_t> fullRgb;
    BaselineJpeg dec;
    if (!dec.decode(fileData.data(), fileData.size(), fullRgb)) {
        result.error_message = "JPEG decode failed";
        return result;
    }

    if (sampleSize > 1) {
        std::vector<uint8_t> small;
        int sw, sh;
        downsample_rgb(fullRgb.data(), origW, origH, sampleSize, small, sw, sh);
        result.data = std::move(small);
        result.width = sw;
        result.height = sh;
    } else {
        result.data = std::move(fullRgb);
        result.width = origW;
        result.height = origH;
    }
    result.is_generated = true;
    result.success = true;
    {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.generated++;
    }
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

// ── Baseline JPEG encoder ──

bool ThumbnailDecoder::encode_jpeg(const uint8_t* rgb, int width, int height,
                                    int quality, std::vector<uint8_t>& jpeg_data) {
    // Baseline JPEG encoding: RGB -> YCbCr 4:4:4 -> 8x8 forward DCT ->
    // quality-scaled quantization -> DC differential + AC run-length Huffman
    // coding. Writes a self-contained baseline JPEG (SOI/DQT/SOF0/DHT/SOS/EOI)
    // that any standard JPEG decoder can read.
    if (!rgb || width <= 0 || height <= 0) {
        LOGW("encode_jpeg: invalid input (%dx%d)", width, height);
        return false;
    }
    if (!encodeBaselineJpeg(rgb, width, height, quality, jpeg_data)) {
        LOGW("encode_jpeg: encoding failed (%dx%d q=%d)", width, height, quality);
        return false;
    }
    LOGI("encode_jpeg: encoded %dx%d q=%d -> %zu bytes",
         width, height, quality, jpeg_data.size());
    return true;
}

} // namespace alcedo
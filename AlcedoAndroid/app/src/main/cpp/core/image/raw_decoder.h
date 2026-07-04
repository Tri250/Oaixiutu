#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <functional>
#include <memory>

namespace alcedo {

// ============================================================
// CFA / Bayer pattern
// ============================================================
enum class CfaPattern {
    RGGB = 0,
    BGGR = 1,
    GRBG = 2,
    GBRG = 3,
    GMCY = 4,  // Fuji X-Trans (simplified as 6x6)
    RGBE = 5,  // Sony 4-color
    CYGM = 6,
    MONO = 7,
    XTRANS = 8,
};

// ============================================================
// Demosaic algorithm
// ============================================================
enum class DemosaicMethod {
    RCD = 0,       // Ratio Corrected Demosaicing (high quality)
    AMAZE = 1,     // Aliasing Minimization and Zipper Elimination
    DCB = 2,       // Demosaicing with Color Balancing
    BILINEAR = 3,  // Simple bilinear interpolation
    VNG4 = 4,      // Variable Number of Gradients
    AHD = 5,       // Adaptive Homogeneity-Directed
    LMMSE = 6,     // Linear Minimum Mean Square Error
};

// ============================================================
// Highlight reconstruction mode
// ============================================================
enum class HighlightMode {
    CLIP = 0,       // Clip to white level
    UNCLIP = 1,     // Use unclipped pixels to reconstruct
    BLEND = 2,      // Blend clipped channels
    RECONSTRUCT = 3, // Full reconstruction (fuse/blend)
};

// ============================================================
// White balance illuminant
// ============================================================
enum class WBIlluminant {
    DAYLIGHT = 0,
    TUNGSTEN = 1,
    FLUORESCENT = 2,
    FLASH = 3,
    CLOUDY = 4,
    SHADE = 5,
    CAMERA_AUTO = 6,
    CAMERA_CUSTOM = 7,
    AS_SHOT = 8,
};

// ============================================================
// RAW image info (read from file header)
// ============================================================
struct RawImageInfo {
    // File format
    std::string format;           // "DNG", "NEF", "CR2", "ARW", etc.
    std::string make;
    std::string model;
    bool is_compressed = false;
    std::string compression_type; // "lossless_jpeg", "lossy_jpeg", "nikon_he", "none"

    // Image dimensions
    int raw_width = 0;
    int raw_height = 0;
    int full_width = 0;   // including masked pixels
    int full_height = 0;
    int top_margin = 0;
    int left_margin = 0;

    // CFA
    CfaPattern cfa_pattern = CfaPattern::RGGB;
    int cfa_repeat_rows = 2;
    int cfa_repeat_cols = 2;
    int cfa_pattern_dim[4] = {2, 2, 0, 0}; // For X-Trans: 6,6

    // Color calibration
    float color_matrix[9] = {1,0,0, 0,1,0, 0,0,1};    // Camera RGB → XYZ (D50)
    float color_matrix_d65[9] = {1,0,0, 0,1,0, 0,0,1};  // Camera RGB → sRGB (D65)
    float forward_matrix[9] = {1,0,0, 0,1,0, 0,0,1};    // XYZ → Camera RGB
    float calibration1[9] = {1,0,0, 0,1,0, 0,0,1};
    float calibration2[9] = {1,0,0, 0,1,0, 0,0,1};
    bool has_color_matrix = false;
    bool has_forward_matrix = false;

    // White balance
    float wb_multipliers[4] = {1.0f, 1.0f, 1.0f, 1.0f}; // RGGB or RGBE
    float daylight_mult[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    float tungsten_mult[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    float camera_wb_mult[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    float as_shot_neutral[4] = {1.0f, 1.0f, 1.0f, 1.0f};

    // Levels
    uint16_t white_level = 65535;
    uint16_t black_level = 0;
    int black_levels[4] = {0, 0, 0, 0};
    int white_levels[4] = {65535, 65535, 65535, 65535};

    // Bits per sample
    int bits_per_sample = 16;
    int samples_per_pixel = 1;

    // Orientation
    int orientation = 1;

    // Thumbnail offset/size in file
    uint32_t thumbnail_offset = 0;
    uint32_t thumbnail_size = 0;
    bool has_thumbnail = false;

    // Preview JPEG offset/size
    uint32_t preview_offset = 0;
    uint32_t preview_size = 0;
    bool has_preview = false;

    // DNG specific
    float baseline_exposure = 0.0f;
    int calibration_illuminant1 = 17; // Standard Light A
    int calibration_illuminant2 = 21; // D65
    float analog_balance[4] = {1.0f, 1.0f, 1.0f, 1.0f};

    // Nikon HE specific
    bool is_nikon_he = false;
    bool is_nikon_he_star = false;
    int nikon_compression_level = 0;

    // Strip / tile offsets
    struct StripInfo {
        uint32_t offset;
        uint32_t byte_count;
    };
    std::vector<StripInfo> strips;
    int rows_per_strip = 0;
    int compression = 1; // 1=uncompressed, 7=lossless JPEG, 8=deflate, 65000=Nikon HE

    bool is_valid = false;
};

// ============================================================
// Decoded RAW result
// ============================================================
struct RawDecodeResult {
    // Full-resolution RGB data (linear, 16-bit or float)
    std::vector<uint16_t> rgb_data;     // 16-bit linear RGB, interleaved
    std::vector<float> float_rgb_data;  // 32-bit float linear RGB
    int width = 0;
    int height = 0;
    int channels = 3;

    // Raw CFA data (before demosaic)
    std::vector<uint16_t> raw_cfa_data;

    // Embedded preview / thumbnail
    std::vector<uint8_t> jpeg_preview;
    std::vector<uint8_t> jpeg_thumbnail;
    int preview_width = 0;
    int preview_height = 0;
    int thumbnail_width = 0;
    int thumbnail_height = 0;

    // Color info
    RawImageInfo image_info;

    // Decoded at reduced resolution
    bool is_half_res = false;
    bool success = false;
    std::string error_message;
};

// ============================================================
// Decode options
// ============================================================
struct RawDecodeOptions {
    DemosaicMethod demosaic = DemosaicMethod::RCD;
    HighlightMode highlight_mode = HighlightMode::RECONSTRUCT;
    WBIlluminant wb_illuminant = WBIlluminant::CAMERA_AUTO;
    float wb_temperature = 6500.0f;
    float wb_tint = 0.0f;
    bool use_camera_matrix = true;
    bool auto_brightness = false;
    float exposure_bias = 0.0f;
    bool half_resolution = false;
    bool output_float = true;
    bool extract_thumbnail = true;
    bool extract_preview = true;
    int max_thumbnail_dimension = 512;
    int max_preview_dimension = 2048;
    bool use_srgb_gamma = false;
    int nikon_he_threads = 4;
};

// ============================================================
// Decode progress callback
// ============================================================
using DecodeProgressCallback = std::function<void(float progress, const std::string& stage)>;

// ============================================================
// RAW Decoder
// ============================================================
class RawDecoder {
public:
    RawDecoder();
    ~RawDecoder();

    // ── File format detection ──
    static std::string detect_format(const std::string& path);
    static std::string detect_format_from_memory(const uint8_t* data, size_t size);
    static bool is_raw_format(const std::string& path);
    static bool is_supported_format(const std::string& path);

    // ── Image info (fast, no full decode) ──
    bool read_image_info(const std::string& path, RawImageInfo& info);
    bool read_image_info_from_memory(const uint8_t* data, size_t size, RawImageInfo& info);

    // ── Full decode ──
    bool decode(const std::string& path, RawDecodeResult& result,
                const RawDecodeOptions& options = RawDecodeOptions());
    bool decode_from_memory(const uint8_t* data, size_t size, RawDecodeResult& result,
                            const RawDecodeOptions& options = RawDecodeOptions());

    // ── Decode with progress ──
    bool decode_with_progress(const std::string& path, RawDecodeResult& result,
                              const RawDecodeOptions& options,
                              DecodeProgressCallback progress_cb);

    // ── Extract thumbnail only ──
    bool extract_thumbnail(const std::string& path, std::vector<uint8_t>& jpeg_data,
                           int& width, int& height);
    bool extract_thumbnail_from_memory(const uint8_t* data, size_t size,
                                        std::vector<uint8_t>& jpeg_data,
                                        int& width, int& height);

    // ── Extract preview JPEG ──
    bool extract_preview(const std::string& path, std::vector<uint8_t>& jpeg_data,
                         int& width, int& height);
    bool extract_preview_from_memory(const uint8_t* data, size_t size,
                                      std::vector<uint8_t>& jpeg_data,
                                      int& width, int& height);

    // ── Extract RAW CFA data (no demosaic) ──
    bool extract_raw_cfa(const std::string& path, std::vector<uint16_t>& cfa_data,
                         int& width, int& height, RawImageInfo& info);
    bool extract_raw_cfa_from_memory(const uint8_t* data, size_t size,
                                      std::vector<uint16_t>& cfa_data,
                                      int& width, int& height, RawImageInfo& info);

    // ── Demosaic operations ──
    static void demosaic_rcd(const uint16_t* raw, int width, int height,
                             int bayer_pattern, uint16_t white_level,
                             uint16_t black_level, float* rgb_output);
    static void demosaic_bilinear(const uint16_t* raw, int width, int height,
                                  int bayer_pattern, uint16_t white_level,
                                  uint16_t black_level, float* rgb_output);
    static void demosaic_dcb(const uint16_t* raw, int width, int height,
                             int bayer_pattern, uint16_t white_level,
                             uint16_t black_level, float* rgb_output);
    static void demosaic_amaze(const uint16_t* raw, int width, int height,
                               int bayer_pattern, uint16_t white_level,
                               uint16_t black_level, float* rgb_output);
    static void demosaic_vng4(const uint16_t* raw, int width, int height,
                              int bayer_pattern, uint16_t white_level,
                              uint16_t black_level, float* rgb_output);

    // ── White balance ──
    static void apply_white_balance_16bit(uint16_t* rgb, int width, int height,
                                          const float* multipliers);
    static void apply_white_balance_float(float* rgb, int width, int height,
                                          const float* multipliers);

    // ── Color matrix transform ──
    static void apply_color_matrix_float(float* rgb, int pixel_count,
                                         const float* matrix_3x3);
    static void apply_color_matrix_16bit(uint16_t* rgb, int pixel_count,
                                         const float* matrix_3x3);

    // ── Highlight reconstruction ──
    static void reconstruct_highlights(float* rgb, int width, int height,
                                       uint16_t white_level,
                                       HighlightMode mode);

    // ── Black level subtraction ──
    static void subtract_black_level(uint16_t* raw, int width, int height,
                                     const int* black_levels, int black_count);

    // ── Scale to 16-bit ──
    static void scale_to_16bit(uint16_t* raw, int width, int height,
                               int bits_per_sample);

    // ── Generate thumbnail from decoded RGB ──
    static void generate_thumbnail(const uint16_t* rgb, int width, int height,
                                   int thumb_width, int thumb_height,
                                   std::vector<uint8_t>& jpeg_thumbnail);

    // ── Generate thumbnail from decoded float RGB ──
    static void generate_thumbnail_float(const float* rgb, int width, int height,
                                         int thumb_width, int thumb_height,
                                         std::vector<uint8_t>& jpeg_thumbnail);

    // ── Nikon HE decompression helpers ──
    static bool decompress_nikon_he(const uint8_t* compressed_data, size_t compressed_size,
                                     uint16_t* output, int width, int height,
                                     int bits_per_sample, int compression_level,
                                     int num_threads = 4);
    static bool is_nikon_he_format(const uint8_t* data, size_t size);

    // ── Nikon HE internal helpers ──
    static bool decompress_nikon_he_lzfse(const uint8_t* src, size_t src_size,
                                           uint8_t* dst, size_t dst_capacity,
                                           size_t& out_size);
    static bool decompress_nikon_he_packed(const uint8_t* data, size_t size,
                                            uint16_t* output, int width, int height,
                                            int bits_per_sample);
    static uint16_t unpack_bits(const uint8_t* buf, size_t buf_size,
                                size_t bit_offset, int num_bits);

    // ── Lossless JPEG decompression (for CR2/NEF/etc) ──
    static bool decompress_lossless_jpeg(const uint8_t* data, size_t size,
                                          uint16_t* output, int width, int height,
                                          int bits_per_sample, int predictor = 1);

private:
    // ── Internal TIFF/DNG parsing ──
    struct TiffContext {
        bool little_endian = true;
        uint32_t ifd0_offset = 0;
        const uint8_t* data = nullptr;
        size_t size = 0;
    };

    struct TiffEntry {
        uint16_t tag;
        uint16_t type;
        uint32_t count;
        uint32_t offset;
        const uint8_t* data_ptr;
    };

    static bool parse_tiff_header(const uint8_t* data, size_t size, TiffContext& ctx);
    static bool read_tiff_entry(const TiffContext& ctx, uint32_t ifd_offset,
                                uint16_t index, TiffEntry& entry);
    static uint16_t tiff_get_uint16(const TiffContext& ctx, const TiffEntry& entry);
    static uint32_t tiff_get_uint32(const TiffContext& ctx, const TiffEntry& entry);
    static float tiff_get_rational(const TiffContext& ctx, const TiffEntry& entry);
    static float tiff_get_srational(const TiffContext& ctx, const TiffEntry& entry);
    static std::string tiff_get_string(const TiffContext& ctx, const TiffEntry& entry);

    static bool parse_dng_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info);
    static bool parse_raw_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info);
    static bool parse_exif_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info);

    static bool find_jpeg_marker(const uint8_t* data, size_t size, size_t& offset,
                                 uint8_t& marker);
    static bool extract_jpeg_from_app1(const uint8_t* data, size_t size,
                                        std::vector<uint8_t>& jpeg,
                                        int& width, int& height);

    // ── Internal state ──
    RawDecodeOptions current_options_;
    DecodeProgressCallback progress_cb_;
    void report_progress(float progress, const std::string& stage);
};

// ============================================================
// TIFF / DNG tag constants
// ============================================================
namespace TiffTags {
    constexpr uint16_t NEW_SUBFILE_TYPE         = 0x00FE;
    constexpr uint16_t SUBFILE_TYPE             = 0x00FF;
    constexpr uint16_t IMAGE_WIDTH              = 0x0100;
    constexpr uint16_t IMAGE_LENGTH             = 0x0101;
    constexpr uint16_t BITS_PER_SAMPLE          = 0x0102;
    constexpr uint16_t COMPRESSION              = 0x0103;
    constexpr uint16_t PHOTOMETRIC              = 0x0106;
    constexpr uint16_t STRIP_OFFSETS            = 0x0111;
    constexpr uint16_t ORIENTATION              = 0x0112;
    constexpr uint16_t SAMPLES_PER_PIXEL        = 0x0115;
    constexpr uint16_t ROWS_PER_STRIP           = 0x0116;
    constexpr uint16_t STRIP_BYTE_COUNTS        = 0x0117;
    constexpr uint16_t X_RESOLUTION             = 0x011A;
    constexpr uint16_t Y_RESOLUTION             = 0x011B;
    constexpr uint16_t PLANAR_CONFIG            = 0x011C;
    constexpr uint16_t MAKE                     = 0x010F;
    constexpr uint16_t MODEL                    = 0x0110;
    constexpr uint16_t SOFTWARE                 = 0x0131;
    constexpr uint16_t DATE_TIME                = 0x0132;
    constexpr uint16_t EXIF_IFD                 = 0x8769;
    constexpr uint16_t DNG_VERSION              = 0xC612;
    constexpr uint16_t DNG_BACKWARD_VERSION     = 0xC613;
    constexpr uint16_t UNIQUE_CAMERA_MODEL      = 0xC614;
    constexpr uint16_t COLOR_MATRIX1            = 0xC621;
    constexpr uint16_t COLOR_MATRIX2            = 0xC622;
    constexpr uint16_t CAMERA_CALIBRATION1      = 0xC623;
    constexpr uint16_t CAMERA_CALIBRATION2      = 0xC624;
    constexpr uint16_t ANALOG_BALANCE           = 0xC627;
    constexpr uint16_t AS_SHOT_NEUTRAL          = 0xC628;
    constexpr uint16_t BASELINE_EXPOSURE        = 0xC62A;
    constexpr uint16_t WHITE_LEVEL              = 0xC61D;
    constexpr uint16_t BLACK_LEVEL              = 0xC61A;
    constexpr uint16_t BLACK_LEVEL_REPEAT_DIM   = 0xC61B;
    constexpr uint16_t BLACK_LEVEL_DELTA_H      = 0xC61C;
    constexpr uint16_t BLACK_LEVEL_DELTA_V      = 0xC61D;
    constexpr uint16_t CFA_PATTERN_DIM          = 0x828D;
    constexpr uint16_t CFA_PATTERN              = 0x828E;
    constexpr uint16_t CFA_REPEAT_PATTERN_DIM   = 0x828D;
    constexpr uint16_t CFA_LAYOUT               = 0xC617;
    constexpr uint16_t CFA_PLANE_COLOR          = 0xC616;
    constexpr uint16_t FORWARD_MATRIX1          = 0xC714;
    constexpr uint16_t FORWARD_MATRIX2          = 0xC715;
    constexpr uint16_t CALIBRATION_ILLUMINANT1  = 0xC65A;
    constexpr uint16_t CALIBRATION_ILLUMINANT2  = 0xC65B;
    constexpr uint16_t DNG_OPCODE_LIST1         = 0xC740;
    constexpr uint16_t DNG_OPCODE_LIST2         = 0xC741;
    constexpr uint16_t DNG_OPCODE_LIST3         = 0xC742;
    constexpr uint16_t DEFAULT_CROP_ORIGIN      = 0xC61F;
    constexpr uint16_t DEFAULT_CROP_SIZE        = 0xC620;
    constexpr uint16_t ACTIVE_AREA              = 0xC68D;
    constexpr uint16_t MASKED_AREAS             = 0xC68E;
    constexpr uint16_t BEST_QUALITY_SCALE       = 0xC65C;
    constexpr uint16_t LINEARIZATION_TABLE       = 0xC618;
    constexpr uint16_t NOISE_PROFILE            = 0xC761;
    constexpr uint16_t PREVIEW_IMAGE_START      = 0xC6D0;
    constexpr uint16_t PREVIEW_IMAGE_LENGTH     = 0xC6D1;
    constexpr uint16_t THUMBNAIL_OFFSET         = 0x0201; // JPEG IFD
    constexpr uint16_t THUMBNAIL_LENGTH         = 0x0202;
    constexpr uint16_t JPEG_INTERCHANGE_FORMAT  = 0x0201;
    constexpr uint16_t JPEG_INTERCHANGE_LENGTH  = 0x0202;
    constexpr uint16_t SUB_IFDS                 = 0x014A;
    constexpr uint16_t NEW_SUB_FILE_TYPE        = 0x00FE;
    constexpr uint16_t TIFF_EP_STANDARD_ID      = 0x9216;
    constexpr uint16_t MAKER_NOTE               = 0x927C;
    constexpr uint16_t SENSOR_WIDTH             = 0xC610;
    constexpr uint16_t SENSOR_HEIGHT            = 0xC611;
    constexpr uint16_t SENSOR_TOP_MARGIN        = 0xC615;
    constexpr uint16_t SENSOR_LEFT_MARGIN       = 0xC614;
    constexpr uint16_t SENSOR_BOTTOM_MARGIN     = 0xC616;
    constexpr uint16_t SENSOR_RIGHT_MARGIN      = 0xC617;
    constexpr uint16_t LOSSY_JPEG_COMPRESSION   = 0xC68F;
}

} // namespace alcedo
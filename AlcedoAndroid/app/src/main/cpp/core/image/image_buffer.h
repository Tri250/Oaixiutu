#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>
#include <functional>
#include <memory>
#include <mutex>

namespace alcedo {

// ============================================================
// Image Buffer
// ============================================================

enum class PixelFormat {
    UINT8_RGBA = 0,
    FLOAT32_RGBA = 1,
    FLOAT16_RGBA = 2,  // Half-float
    UINT16_RGBA = 3,
    UINT16_RAW = 4,    // Single-channel RAW (Bayer)
    FLOAT32_RAW = 5,
    FLOAT32_RGB = 6,
    UINT8_RGB = 7,
    FLOAT32_PLANAR = 8  // Multi-plane
};

enum class BufferBackend {
    CPU = 0,
    GPU_GLES = 1,
    GPU_VK = 2,
    GPU_MTL = 3
};

struct ImageBuffer {
    int width = 0;
    int height = 0;
    int channels = 4;
    PixelFormat format = PixelFormat::FLOAT32_RGBA;
    BufferBackend backend = BufferBackend::CPU;

    // CPU data
    std::vector<uint8_t> cpu_data;
    int row_stride = 0;  // Bytes per row (may differ from width * pixel_size)

    // GPU handles
    void* gpu_texture_id = nullptr;     // GLuint / VkImage
    void* gpu_buffer_handle = nullptr;  // EGLImage / AHardwareBuffer
    bool gpu_data_valid = false;

    // Multi-plane support (for RAW / YUV)
    struct Plane {
        std::vector<uint8_t> data;
        int width = 0;
        int height = 0;
        int row_stride = 0;
    };
    std::vector<Plane> planes;

    // Metadata
    std::string color_space = "sRGB";
    float display_peak_luminance = 100.0f;
    bool is_hdr = false;
    bool is_raw = false;

    // Construction
    ImageBuffer() = default;
    ImageBuffer(int w, int h, PixelFormat fmt = PixelFormat::FLOAT32_RGBA);

    // Allocation
    void allocate(int w, int h, PixelFormat fmt = PixelFormat::FLOAT32_RGBA);
    void allocate_planes(int w, int h, int num_planes);
    void release();

    // Access
    int pixel_size() const;
    size_t total_bytes() const;
    bool is_valid() const;

    // Float access (for 32-bit float processing)
    float* float_data();
    const float* float_data() const;
    int float_count() const;

    // UInt8 access
    uint8_t* uint8_data();
    const uint8_t* uint8_data() const;

    // UInt16 access
    uint16_t* uint16_data();
    const uint16_t* uint16_data() const;

    // Conversion
    void convert_to_float32();
    void convert_to_uint8();
    void convert_to_float16();
    void convert_to_uint16();

    // Copy
    void copy_from(const ImageBuffer& other);
    ImageBuffer clone() const;

    // GPU
    void upload_to_gpu();
    void download_from_gpu();
    void release_gpu();

    // Memory pool
    static ImageBuffer* acquire_from_pool(int w, int h, PixelFormat fmt);
    static void return_to_pool(ImageBuffer* buf);
    static void clear_pool();
};

// ============================================================
// Pipeline Parameters
// ============================================================

struct RawDecodeParams {
    int demosaic_algorithm = 0;  // 0=RCD, 1=AMAZE, 2=DCB, 3=SIMPLE
    bool highlight_reconstruction = true;
    bool auto_brightness = false;
    bool use_camera_matrix = true;
    int bayer_pattern = 0;  // 0=RGGB, 1=BGGR, 2=GRBG, 3=GBRG
    uint16_t white_level = 65535;
    uint16_t black_level = 0;
};

struct DisplayTransform {
    int color_science = 0;  // 0=ACES20, 1=OPENDRT, 2=LINEAR
    int eotf = 0;           // 0=SRGB, 1=PQ, 2=HLG, 3=GAMMA22, 4=GAMMA24
    float peak_luminance = 100.0f;
    int display_color_space = 0; // 0=SRGB, 1=DISPLAY_P3, 2=REC2020
};

struct PipelineParams {
    // Auto exposure
    bool auto_exposure_enabled = false;
    float auto_exposure_target_percentile = 0.5f;
    float auto_exposure_target_luminance = 0.18f;
    float auto_exposure_value = 0.0f; // computed result in EV

    // Exposure
    float exposure = 0.0f;
    float contrast = 0.0f;
    float sigmoid_contrast = 0.0f;

    // White balance
    float white_balance_temp = 6500.0f;
    float white_balance_tint = 0.0f;

    // Tone
    float highlights = 0.0f;
    float shadows = 0.0f;
    float midtones = 0.0f;
    float shadow_boundary = 0.25f;
    float highlight_boundary = 0.75f;

    // Tone curve
    float tone_curve_x[16] = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
    float tone_curve_y[16] = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
    int tone_curve_points = 5;

    // Color
    float saturation = 0.0f;
    float vibrance = 0.0f;

    // Tint (split toning)
    float tint_highlight_hue = 0.0f;
    float tint_highlight_strength = 0.0f;
    float tint_shadow_hue = 0.0f;
    float tint_shadow_strength = 0.0f;
    float tint_balance = 0.0f;

    // Color wheels (CDL)
    float color_wheel_lift[3] = {0.0f, 0.0f, 0.0f};
    float color_wheel_gamma[3] = {1.0f, 1.0f, 1.0f};
    float color_wheel_gain[3] = {1.0f, 1.0f, 1.0f};

    // HSL
    float hsl_hue_ranges[8] = {0.0f, 45.0f, 90.0f, 135.0f, 180.0f, 225.0f, 270.0f, 315.0f};
    float hsl_hue_width = 60.0f;
    float hsl_hue_shift[8] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    float hsl_saturation_scale[8] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    float hsl_luminance_scale[8] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};

    // Channel mixer
    float channel_mixer_matrix[9] = {1.0f,0,0, 0,1.0f,0, 0,0,1.0f};
    bool channel_mixer_monochrome = false;

    // Clarity
    float clarity = 0.0f;
    float clarity_radius = 15.0f;

    // Sharpen
    float sharpen = 0.0f;

    // Effects
    float film_grain = 0.0f;
    float halation_intensity = 0.0f;
    float halation_threshold = 0.8f;
    float halation_spread = 10.0f;
    float halation_red_bias = 0.7f;

    // LUT
    std::string lut_path;
    bool lut_enabled = false;

    // Denoise
    float luminance_denoise_strength = 0.0f;
    float luminance_denoise_detail = 0.5f;
    float chroma_denoise_strength = 0.0f;
    float chroma_denoise_threshold = 0.5f;

    // Geometry
    float geometry_rotate = 0.0f;
    float geometry_scale = 1.0f;
    float geometry_crop_left = 0.0f;
    float geometry_crop_top = 0.0f;
    float geometry_crop_right = 1.0f;
    float geometry_crop_bottom = 1.0f;
    float geometry_perspective_src[8] = {0,0, 1,0, 1,1, 0,1};
    float geometry_perspective_dst[8] = {0,0, 1,0, 1,1, 0,1};

    // Lens correction
    float lens_k1 = 0.0f;
    float lens_k2 = 0.0f;
    float lens_k3 = 0.0f;
    float lens_p1 = 0.0f;
    float lens_p2 = 0.0f;
    float lens_cx = 0.5f;
    float lens_cy = 0.5f;
    float lens_focal_ratio = 1.0f;
    float lens_vignette_strength = 0.0f;

    // DNG warp
    float dng_warp_coeffs[4] = {0.0f, 0.0f, 0.0f, 0.0f};

    // Display transform
    DisplayTransform display_transform;

    // RAW decode
    RawDecodeParams raw_decode_params;
};

// ============================================================
// Pipeline Stage
// ============================================================

enum class PipelineStage {
    RAW_DECODE = 0,
    HIGHLIGHT_RECONSTRUCTION = 1,
    DEMOSAIC = 2,
    AUTO_EXPOSURE = 3,
    EXPOSURE = 4,
    WHITE_BALANCE = 5,
    TONE = 6,
    TONE_CURVE = 7,
    COLOR = 8,
    CLARITY = 9,
    SHARPEN = 10,
    EFFECTS = 11,
    GEOMETRY = 12,
    DISPLAY_TRANSFORM = 13,
    FINAL = 14
};

// ============================================================
// Pipeline Service
// ============================================================

class PipelineService {
public:
    static PipelineService& Instance();

    PipelineService();
    ~PipelineService();

    // Configure pipeline
    void set_backend(BufferBackend backend);
    void set_working_color_space(int space); // 0=sRGB, 4=ACES AP1
    void enable_stage(PipelineStage stage, bool enable);
    bool is_stage_enabled(PipelineStage stage) const;

    // Process a float buffer through the full pipeline
    // Input: 32-bit float RGBA or RGB pixels
    // The pipeline processes in-place.
    bool process(float* pixels, int width, int height, int channels,
                 const PipelineParams& params);

    // Process with GPU fallback to CPU on failure
    bool process_with_gpu_fallback(float* pixels, int width, int height, int channels,
                                   const PipelineParams& params);

    // Process with RAW input
    bool process_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                     float* output_rgb, int output_width, int output_height,
                     const PipelineParams& params);

    // Process a single stage
    bool process_stage(PipelineStage stage, float* pixels, int width, int height,
                       int channels, const PipelineParams& params);

    // RAW decode
    bool decode_raw(const uint16_t* raw_data, int raw_width, int raw_height,
                    float* output_rgb, int output_width, int output_height,
                    const RawDecodeParams& raw_params);

    // Display transform
    void apply_display_transform(float* pixels, int width, int height, int channels,
                                  const DisplayTransform& transform);

    // Auto exposure: compute recommended exposure value (EV)
    float compute_auto_exposure(const float* pixels, int width, int height, int channels,
                                float target_percentile = 0.5f,
                                float target_luminance = 0.18f);

    // Get pipeline info
    std::string get_pipeline_info() const;

private:
    BufferBackend backend_ = BufferBackend::CPU;
    int working_color_space_ = 0; // sRGB linear
    bool stage_enabled_[15] = {true}; // All enabled by default

    static std::once_flag init_flag_;
    static std::unique_ptr<PipelineService> instance_;

    // Internal helpers
    void apply_auto_exposure(float* pixels, int width, int height, int channels, PipelineParams& params);
    void apply_exposure(float* pixels, int count, int channels, float exposure);
    void apply_contrast(float* pixels, int count, int channels, float contrast);
    void apply_white_balance(float* pixels, int count, int channels, float temp, float tint);
    void apply_tone_regions(float* pixels, int count, int channels, const PipelineParams& params);
    void apply_tone_curve(float* pixels, int count, int channels, const PipelineParams& params);
    void apply_color(float* pixels, int count, int channels, const PipelineParams& params);
    void apply_clarity(float* pixels, int width, int height, int channels, const PipelineParams& params);
    void apply_sharpen(float* pixels, int width, int height, int channels, float amount);
    void apply_effects(float* pixels, int width, int height, int channels, const PipelineParams& params);
    void apply_geometry(float* pixels, int width, int height, int channels, const PipelineParams& params);
};

// ============================================================
// Pipeline Snapshot
// ============================================================

// Read-only clone of pipeline state for background analysis rendering.
// Does not interfere with the active pipeline and can be released independently.
class PipelineSnapshot {
public:
    PipelineSnapshot(int width, int height, int channels, const PipelineParams& params);
    ~PipelineSnapshot();

    // Create from existing pixel data (makes a deep copy)
    static std::unique_ptr<PipelineSnapshot> create(const float* pixels, int width, int height,
                                                     int channels, const PipelineParams& params);

    // Render the snapshot through a simplified pipeline (read-only, no side effects)
    bool render(float* output, int output_width, int output_height) const;

    // Accessors
    const float* data() const { return data_.data(); }
    int width() const { return width_; }
    int height() const { return height_; }
    int channels() const { return channels_; }
    const PipelineParams& params() const { return params_; }

    // Release snapshot data
    void release();

    bool is_valid() const { return !data_.empty(); }

private:
    std::vector<float> data_;
    int width_ = 0;
    int height_ = 0;
    int channels_ = 0;
    PipelineParams params_;
};

} // namespace alcedo
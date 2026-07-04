#pragma once

#include <cstddef>
#include <memory>

namespace alcedo {

enum class OperatorType {
    RAW_DECODE,
    RESIZE,
    EXPOSURE,
    CONTRAST,
    WHITE,
    BLACK,
    SHADOWS,
    HIGHLIGHTS,
    CURVE,
    HLS,
    SATURATION,
    TINT,
    VIBRANCE,
    CST,
    CLARITY,
    SHARPEN,
    COLOR_WHEEL,
    FILM_GRAIN,
    HALATION,
    CROP_ROTATE,
    HSL,
    CHANNEL_MIXER,
    LUT,
    LENS_CORRECTION,
    GEOMETRY,
    HIGHLIGHT_RECONSTRUCTION,
    DEMOSAIC,
    AUTO_EXPOSURE,
    TONE_REGION,
    COLOR_TEMP,
    ODT,
    LMT,
    CV_CVT,
    UNKNOWN
};

enum class PipelineStageName {
    Image_Loading = 0,
    Geometry_Adjustment = 1,
    To_WorkingSpace = 2,
    Basic_Adjustment = 3,
    Color_Adjustment = 4,
    Detail_Adjustment = 5,
    Output_Transform = 6
};

class IOperatorBase {
public:
    virtual ~IOperatorBase() = default;
    virtual void Apply(float* pixels, int width, int height, int channels) = 0;
    virtual OperatorType GetType() const = 0;
};

template<typename Derived>
class OperatorBase : public IOperatorBase {
public:
    void Apply(float* pixels, int width, int height, int channels) override {
        static_cast<Derived*>(this)->ApplyImpl(pixels, width, height, channels);
    }

    OperatorType GetType() const override {
        return static_cast<const Derived*>(this)->GetTypeImpl();
    }
};

} // namespace alcedo

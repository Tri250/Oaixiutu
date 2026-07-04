#pragma once
#include "operator_base.h"
#include <algorithm>

namespace alcedo {

// Color temperature adjustment using Planckian locus lookup
// Ported from desktop color_temp_op.cpp - uses Adobe DNG SDK model
// Supports AS_SHOT and CUSTOM modes, CCT range 2000K-15000K, tint ±150
class ColorTempOp : public OperatorBase<ColorTempOp> {
public:
    enum class Mode : int { AS_SHOT = 0, CUSTOM = 1 };

    ColorTempOp();
    explicit ColorTempOp(float cct, float tint, Mode mode = Mode::CUSTOM);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::COLOR_TEMP; }

    void SetCustomCCT(float cct) { custom_cct_ = std::clamp(cct, 2000.0f, 15000.0f); }
    void SetCustomTint(float tint) { custom_tint_ = std::clamp(tint, -150.0f, 150.0f); }
    void SetMode(Mode mode) { mode_ = mode; }

    float GetCCT() const { return custom_cct_; }
    float GetTint() const { return custom_tint_; }
    Mode GetMode() const { return mode_; }

private:
    Mode mode_ = Mode::AS_SHOT;
    float custom_cct_ = 6500.0f;
    float custom_tint_ = 0.0f;
    // Camera matrices (populated from RAW metadata)
    float cam_to_xyz_[9] = {1,0,0, 0,1,0, 0,0,1};
    float xyz_d50_to_ap1_[9] = {
        1.641023f, -0.324803f, -0.236425f,
        -0.663663f,  1.615332f,  0.016756f,
         0.011722f, -0.008284f,  0.988395f
    };
    float as_shot_cct_ = 6500.0f;
    float as_shot_tint_ = 0.0f;

    void ComputeTemperatureMatrix(float cct, float tint, float* out_3x3);
};

} // namespace alcedo

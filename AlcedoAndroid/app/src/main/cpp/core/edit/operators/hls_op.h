#pragma once
#include "operator_base.h"

namespace alcedo {

// HLS per-profile color adjustment
// 8 hue profiles (0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°)
// Ported from desktop HLS_op.cpp - ACEScc-based hue-selective editing
class HLSOp : public OperatorBase<HLSOp> {
public:
    static constexpr int kProfileCount = 8;

    HLSOp();

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::HLS; }

    // Set hue adjustment for a specific profile index
    void SetHueAdjustment(int profile, float hue_shift);
    void SetLightnessAdjustment(int profile, float lightness);
    void SetSaturationAdjustment(int profile, float saturation);
    void SetHueRange(int profile, float range);

    float GetHueAdjustment(int profile) const { return hls_adjustments_[profile][0]; }
    float GetLightnessAdjustment(int profile) const { return hls_adjustments_[profile][1]; }
    float GetSatAdjustment(int profile) const { return hls_adjustments_[profile][2]; }

private:
    float hue_profiles_[kProfileCount] = {0,45,90,135,180,225,270,315};
    float hls_adjustments_[kProfileCount][3] = {}; // [hue_shift, lightness, saturation]
    float hue_ranges_[kProfileCount] = {45,45,45,45,45,45,45,45};

    // ACEScc encode/decode (log2-based)
    static float AcesccEncode(float linear);
    static float AcesccDecode(float acescc);
    static float WrapHue(float h);
    static float HueDistance(float a, float b);
    static float Smoothstep(float e0, float e1, float x);
};

} // namespace alcedo

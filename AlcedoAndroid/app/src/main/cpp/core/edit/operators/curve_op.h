#pragma once
#include "operator_base.h"
#include <vector>

namespace alcedo {

// Hermite monotone-preserving spline curve adjustment
// Ported from desktop curve_op.cpp - Fritsch-Carlson monotone tangents
// Applies curve to luminance with 0.65 influence factor
class CurveOp : public OperatorBase<CurveOp> {
public:
    struct ControlPoint { float x, y; };

    CurveOp();
    explicit CurveOp(const std::vector<ControlPoint>& pts);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::CURVE; }

    void SetControlPoints(const std::vector<ControlPoint>& pts);
    const std::vector<ControlPoint>& GetControlPoints() const { return ctrl_pts_; }

private:
    std::vector<ControlPoint> ctrl_pts_;
    std::vector<float> h_; // interval widths
    std::vector<float> m_; // tangent values

    void ComputeTangents(); // Fritsch-Carlson monotone algorithm
    float Evaluate(float x) const; // Hermite basis function evaluation
    void Prepare(); // Sort, dedup, clamp, compute tangents
};

} // namespace alcedo

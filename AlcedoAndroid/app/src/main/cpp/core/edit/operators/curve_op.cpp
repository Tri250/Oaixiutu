#include "curve_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

CurveOp::CurveOp() {
    // Default: identity curve (0,0) → (1,1)
    ctrl_pts_ = {{0.0f, 0.0f}, {1.0f, 1.0f}};
    Prepare();
}

CurveOp::CurveOp(const std::vector<ControlPoint>& pts) : ctrl_pts_(pts) {
    Prepare();
}

void CurveOp::SetControlPoints(const std::vector<ControlPoint>& pts) {
    ctrl_pts_ = pts;
    Prepare();
}

void CurveOp::Prepare() {
    int n = static_cast<int>(ctrl_pts_.size());
    if (n < 2) {
        ctrl_pts_ = {{0.0f, 0.0f}, {1.0f, 1.0f}};
        n = 2;
    }

    // Sort by x
    std::sort(ctrl_pts_.begin(), ctrl_pts_.end(),
              [](const ControlPoint& a, const ControlPoint& b) { return a.x < b.x; });

    // Deduplicate (remove points with same x, keep last)
    std::vector<ControlPoint> deduped;
    for (int i = 0; i < n; ++i) {
        if (i > 0 && std::fabs(ctrl_pts_[i].x - deduped.back().x) < 1e-6f) {
            deduped.back() = ctrl_pts_[i];
        } else {
            deduped.push_back(ctrl_pts_[i]);
        }
    }
    ctrl_pts_ = std::move(deduped);
    n = static_cast<int>(ctrl_pts_.size());

    // Clamp x to [0,1] and y to reasonable range
    for (auto& pt : ctrl_pts_) {
        pt.x = std::clamp(pt.x, 0.0f, 1.0f);
        pt.y = std::clamp(pt.y, 0.0f, 1.0f);
    }

    // Compute interval widths
    h_.resize(n - 1);
    for (int i = 0; i < n - 1; ++i) {
        h_[i] = ctrl_pts_[i + 1].x - ctrl_pts_[i].x;
        if (h_[i] < 1e-10f) h_[i] = 1e-10f; // Safety
    }

    ComputeTangents();
}

void CurveOp::ComputeTangents() {
    int n = static_cast<int>(ctrl_pts_.size());
    m_.resize(n);

    if (n < 2) return;

    // Step 1: Compute initial tangents using centered differences
    // For endpoints, use one-sided differences
    m_[0] = (ctrl_pts_[1].y - ctrl_pts_[0].y) / h_[0];
    m_[n - 1] = (ctrl_pts_[n - 1].y - ctrl_pts_[n - 2].y) / h_[n - 2];

    for (int i = 1; i < n - 1; ++i) {
        float delta_y = (ctrl_pts_[i + 1].y - ctrl_pts_[i - 1].y);
        float delta_x = h_[i - 1] + h_[i];
        m_[i] = delta_y / std::max(delta_x, 1e-10f);
    }

    // Step 2: Fritsch-Carlson monotone constraint
    // Ensure monotonicity by adjusting tangents where needed
    for (int i = 0; i < n - 1; ++i) {
        float delta = (ctrl_pts_[i + 1].y - ctrl_pts_[i].y) / h_[i];

        // If the segment is flat, force zero tangents
        if (std::fabs(delta) < 1e-10f) {
            m_[i] = 0.0f;
            m_[i + 1] = 0.0f;
            continue;
        }

        // Check if tangent at left point needs clipping
        float alpha = m_[i] / delta;
        float beta = m_[i + 1] / delta;

        // If both tangents have the same sign as delta, apply monotone constraint
        if (alpha > 0.0f && beta > 0.0f) {
            // Fritsch-Carlson: limit sum of normalized tangents to 3
            float sum = alpha + beta;
            if (sum > 3.0f) {
                // Scale tangents proportionally to satisfy the constraint
                m_[i] = delta * 3.0f * alpha / sum;
                m_[i + 1] = delta * 3.0f * beta / sum;
            }
        }
    }
}

float CurveOp::Evaluate(float x) const {
    int n = static_cast<int>(ctrl_pts_.size());
    if (n < 2) return x;

    // Clamp x to range
    if (x <= ctrl_pts_[0].x) return ctrl_pts_[0].y;
    if (x >= ctrl_pts_[n - 1].x) return ctrl_pts_[n - 1].y;

    // Find the interval
    int seg = 0;
    for (int i = 0; i < n - 1; ++i) {
        if (x >= ctrl_pts_[i].x && x <= ctrl_pts_[i + 1].x) {
            seg = i;
            break;
        }
    }

    // Normalized position within segment
    float t = (x - ctrl_pts_[seg].x) / h_[seg];
    t = std::clamp(t, 0.0f, 1.0f);

    float t2 = t * t;
    float t3 = t2 * t;

    // Hermite basis functions
    float h00 = 2.0f * t3 - 3.0f * t2 + 1.0f;  // value at left
    float h10 = t3 - 2.0f * t2 + t;               // tangent at left (scaled by h)
    float h01 = -2.0f * t3 + 3.0f * t2;           // value at right
    float h11 = t3 - t2;                            // tangent at right (scaled by h)

    float p0 = ctrl_pts_[seg].y;
    float p1 = ctrl_pts_[seg + 1].y;
    float m0 = m_[seg] * h_[seg];
    float m1 = m_[seg + 1] * h_[seg];

    return h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
}

void CurveOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    size_t total = static_cast<size_t>(width) * height;

    // If only 2 control points forming identity, skip
    if (ctrl_pts_.size() == 2 &&
        std::fabs(ctrl_pts_[0].x - 0.0f) < 1e-6f &&
        std::fabs(ctrl_pts_[0].y - 0.0f) < 1e-6f &&
        std::fabs(ctrl_pts_[1].x - 1.0f) < 1e-6f &&
        std::fabs(ctrl_pts_[1].y - 1.0f) < 1e-6f) {
        return;
    }

    // Luminance coefficients (Rec.709)
    static constexpr float kLumR = 0.2126f;
    static constexpr float kLumG = 0.7152f;
    static constexpr float kLumB = 0.0722f;

    // Influence factor: curve affects luminance with 0.65 influence
    static constexpr float kInfluence = 0.65f;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        // Compute luminance
        float lum = kLumR * r + kLumG * g + kLumB * b;

        // Apply curve to luminance
        float curve_lum = Evaluate(std::clamp(lum, 0.0f, 1.0f));

        if (lum > 1e-6f) {
            // Scale RGB proportionally with influence blending
            float scale = curve_lum / lum;
            float blended_scale = 1.0f + (scale - 1.0f) * kInfluence;

            pixels[idx]     = std::clamp(r * blended_scale, 0.0f, 1.0f);
            pixels[idx + 1] = std::clamp(g * blended_scale, 0.0f, 1.0f);
            pixels[idx + 2] = std::clamp(b * blended_scale, 0.0f, 1.0f);
        }
    }
}

} // namespace alcedo

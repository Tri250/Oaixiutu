// AlcedoAndroid - CurveOp implementation (monotone cubic Hermite spline).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/curve/curve_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
CurveOp::CurveOp() {
  // Default identity curve.
  ctrl_pts_ = {{0, 0}, {1, 1}};
  RebuildSpline();
}
CurveOp::CurveOp(const nlohmann::json& params) { SetParams(params); }

void CurveOp::RebuildSpline() {
  size_t n = ctrl_pts_.size();
  if (n < 2) { h_.clear(); m_.clear(); return; }
  h_.resize(n - 1);
  for (size_t i = 0; i + 1 < n; ++i) h_[i] = ctrl_pts_[i + 1].x - ctrl_pts_[i].x;
  // Fritsch-Carlson monotone tangents.
  std::vector<float> delta(n - 1);
  for (size_t i = 0; i + 1 < n; ++i) {
    delta[i] = (h_[i] > 1e-6f) ? (ctrl_pts_[i + 1].y - ctrl_pts_[i].y) / h_[i] : 0.0f;
  }
  m_.assign(n, 0.0f);
  m_[0] = delta[0];
  m_[n - 1] = delta[n - 2];
  for (size_t i = 1; i + 1 < n; ++i) {
    if (delta[i - 1] * delta[i] <= 0.0f) m_[i] = 0.0f;
    else m_[i] = (delta[i - 1] + delta[i]) * 0.5f;
  }
  for (size_t i = 0; i + 1 < n; ++i) {
    if (std::abs(delta[i]) < 1e-9f) { m_[i] = 0.0f; m_[i + 1] = 0.0f; }
    else {
      float a = m_[i] / delta[i];
      float b = m_[i + 1] / delta[i];
      float s = a * a + b * b;
      if (s > 9.0f) {
        float t = 3.0f / std::sqrt(s);
        m_[i] = t * a * delta[i];
        m_[i + 1] = t * b * delta[i];
      }
    }
  }
}

void CurveOp::SetControlPoints(const std::vector<CurvePoint>& pts) {
  ctrl_pts_ = pts;
  if (ctrl_pts_.size() < 2) ctrl_pts_ = {{0, 0}, {1, 1}};
  RebuildSpline();
}

float CurveOp::Evaluate(float x) const {
  if (ctrl_pts_.empty()) return x;
  if (x <= ctrl_pts_.front().x) return ctrl_pts_.front().y;
  if (x >= ctrl_pts_.back().x) return ctrl_pts_.back().y;
  size_t n = ctrl_pts_.size();
  size_t i = 0;
  while (i + 1 < n && ctrl_pts_[i + 1].x < x) ++i;
  float h = h_[i];
  if (h < 1e-6f) return ctrl_pts_[i].y;
  float t = (x - ctrl_pts_[i].x) / h;
  float t2 = t * t, t3 = t2 * t;
  float h00 = 2 * t3 - 3 * t2 + 1;
  float h10 = t3 - 2 * t2 + t;
  float h01 = -2 * t3 + 3 * t2;
  float h11 = t3 - t2;
  return h00 * ctrl_pts_[i].y + h10 * h * m_[i] +
         h01 * ctrl_pts_[i + 1].y + h11 * h * m_[i + 1];
}

void CurveOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  img.ForEachPixel([this](Pixel& p, int, int) {
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float scale = (lum > 1e-5f) ? Evaluate(std::clamp(lum, 0.0f, 1.0f)) / lum : 1.0f;
    p.r *= scale; p.g *= scale; p.b *= scale;
  });
}
void CurveOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto CurveOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  nlohmann::json arr = nlohmann::json::array();
  for (auto& pt : ctrl_pts_) arr.push_back({pt.x, pt.y});
  o["points"] = arr;
  return o;
}
void CurveOp::SetParams(const nlohmann::json& params) {
  ctrl_pts_.clear();
  if (params.contains("points")) {
    for (const auto& pt : params["points"]) {
      ctrl_pts_.push_back({pt[0].get<float>(), pt[1].get<float>()});
    }
  }
  if (ctrl_pts_.size() < 2) ctrl_pts_ = {{0, 0}, {1, 1}};
  RebuildSpline();
}
void CurveOp::SetGlobalParams(OperatorParams& params) const {
  params.curve_ctrl_pts_.clear();
  for (auto& pt : ctrl_pts_) params.curve_ctrl_pts_.push_back(pt);
  params.curve_h_ = h_;
  params.curve_m_ = m_;
}
void CurveOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.curve_enabled_ = enable; }
}  // namespace alcedo

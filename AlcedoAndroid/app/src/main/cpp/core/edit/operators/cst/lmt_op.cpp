// AlcedoAndroid - LmtOp implementation (3D LUT sampler).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/lmt_op.hpp"
#include <algorithm>
#include <cmath>
#include <fstream>
#include "image/image_buffer.hpp"
#include "utils/app_logging.hpp"
namespace alcedo {
LmtOp::LmtOp() = default;
LmtOp::LmtOp(const nlohmann::json& params) { SetParams(params); }
bool LmtOp::LoadLut(const std::filesystem::path& path) {
  lut_path_ = path;
  std::ifstream f(path);
  if (!f) { ALOGW("LmtOp: cannot open LUT %s", path.string().c_str()); return false; }
  std::vector<float> data;
  std::string line;
  while (std::getline(f, line)) {
    if (line.empty() || line[0] == '#') continue;
    if (line.rfind("LUT_3D_SIZE", 0) == 0) continue;
    if (line.rfind("LUT_1D_SIZE", 0) == 0) continue;
    std::istringstream ss(line);
    float r, g, b;
    if (ss >> r >> g >> b) { data.push_back(r); data.push_back(g); data.push_back(b); }
  }
  if (data.size() != static_cast<size_t>(kLutSize) * kLutSize * kLutSize * 3) {
    ALOGW("LmtOp: LUT size mismatch (got %zu floats)", data.size());
    // Fall back to identity.
    lut_.assign((size_t)kLutSize * kLutSize * kLutSize * 3, 0.0f);
    for (int ri = 0; ri < kLutSize; ++ri)
      for (int gi = 0; gi < kLutSize; ++gi)
        for (int bi = 0; bi < kLutSize; ++bi) {
          size_t idx = ((size_t)ri * kLutSize + gi) * kLutSize + bi;
          lut_[idx * 3 + 0] = (float)ri / (kLutSize - 1);
          lut_[idx * 3 + 1] = (float)gi / (kLutSize - 1);
          lut_[idx * 3 + 2] = (float)bi / (kLutSize - 1);
        }
  } else {
    lut_ = std::move(data);
  }
  lut_loaded_ = true;
  enabled_ = true;
  return true;
}
void LmtOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (!enabled_ || !lut_loaded_) return;
  FloatMat& img = input->GetCPUData();
  const float s = strength_;
  img.ForEachPixel([this, s](Pixel& p, int, int) {
    auto clamp01 = [](float v) { return std::clamp(v, 0.0f, 1.0f); };
    float r = clamp01(p.r), g = clamp01(p.g), b = clamp01(p.b);
    float fx = r * (kLutSize - 1), fy = g * (kLutSize - 1), fz = b * (kLutSize - 1);
    int x0 = (int)fx, y0 = (int)fy, z0 = (int)fz;
    int x1 = std::min(x0 + 1, kLutSize - 1), y1 = std::min(y0 + 1, kLutSize - 1),
        z1 = std::min(z0 + 1, kLutSize - 1);
    float tx = fx - x0, ty = fy - y0, tz = fz - z0;
    auto sample = [&](int x, int y, int z, int c) {
      size_t idx = ((size_t)x * kLutSize + y) * kLutSize + z;
      return lut_[idx * 3 + c];
    };
    for (int c = 0; c < 3; ++c) {
      float c000 = sample(x0,y0,z0,c), c001 = sample(x0,y0,z1,c);
      float c010 = sample(x0,y1,z0,c), c011 = sample(x0,y1,z1,c);
      float c100 = sample(x1,y0,z0,c), c101 = sample(x1,y0,z1,c);
      float c110 = sample(x1,y1,z0,c), c111 = sample(x1,y1,z1,c);
      float c00 = c000*(1-tz) + c001*tz;
      float c01 = c010*(1-tz) + c011*tz;
      float c10 = c100*(1-tz) + c101*tz;
      float c11 = c110*(1-tz) + c111*tz;
      float c0 = c00*(1-ty) + c01*ty;
      float c1 = c10*(1-ty) + c11*ty;
      float lut_val = c0*(1-tx) + c1*tx;
      float orig = (&p.r)[c];
      (&p.r)[c] = orig * (1.0f - s) + lut_val * s;
    }
  });
}
void LmtOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto LmtOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["lut_path"] = lut_path_.string();
  o["strength"] = strength_;
  o["enabled"]  = enabled_;
  return o;
}
void LmtOp::SetParams(const nlohmann::json& params) {
  strength_ = params.value("strength", 1.0f);
  enabled_  = params.value("enabled", false);
  if (params.contains("lut_path")) {
    auto p = std::filesystem::path(params["lut_path"].get<std::string>());
    if (!p.empty()) LoadLut(p);
  }
}
void LmtOp::SetGlobalParams(OperatorParams& params) const {
  params.lmt_enabled_   = enabled_;
  params.lmt_lut_path_  = lut_path_;
  params.to_output_params_.apply_look_ = enabled_;
  params.to_output_params_.look_strength_ = strength_;
  params.to_lmt_dirty_ = true;
}
void LmtOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.lmt_enabled_ = enable; }
}  // namespace alcedo

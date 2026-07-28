// AlcedoAndroid - Film grain operator (PRNG-based monochromatic grain).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class FilmGrainOp : public OperatorBase<FilmGrainOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Output_Transform;
  static constexpr std::string_view  canonical_name_    = "FilmGrain";
  static constexpr std::string_view  script_name_       = "film_grain";
  static constexpr OperatorType      operator_type_     = OperatorType::FILM_GRAIN;
  FilmGrainOp();
  explicit FilmGrainOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  bool          enabled_      = true;
  float         strength_     = 0.0f;
  float         filter_sigma_ = 0.8f;
  std::uint64_t seed_         = 0x6a09e667f3bcc909ULL;
};
}  // namespace alcedo

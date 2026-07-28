// AlcedoAndroid - Operator registration.
// Registers all concrete operators with the OperatorFactory singleton so the
// pipeline can construct them by type/script-name when importing JSON params.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/operator_factory.hpp"
#include "edit/operators/op_base.hpp"

#include "edit/operators/basic/exposure_op.hpp"
#include "edit/operators/basic/contrast_op.hpp"
#include "edit/operators/basic/white_op.hpp"
#include "edit/operators/basic/black_op.hpp"
#include "edit/operators/basic/shadow_op.hpp"
#include "edit/operators/basic/highlight_op.hpp"
#include "edit/operators/basic/color_temp_op.hpp"
#include "edit/operators/basic/highlight_shadow_local_tone_vulkan.hpp"
#include "edit/operators/color/tint_op.hpp"
#include "edit/operators/color/saturation_op.hpp"
#include "edit/operators/color/vibrance_op.hpp"
#include "edit/operators/color/hls_op.hpp"
#include "edit/operators/curve/curve_op.hpp"
#include "edit/operators/cst/cst_op.hpp"
#include "edit/operators/cst/lmt_op.hpp"
#include "edit/operators/cst/odt_op.hpp"
#include "edit/operators/cst/film_grain_op.hpp"
#include "edit/operators/cst/halation_op.hpp"
#include "edit/operators/detail/clarity_op.hpp"
#include "edit/operators/detail/sharpen_op.hpp"
#include "edit/operators/geometry/resize_op.hpp"
#include "edit/operators/geometry/crop_rotate_op.hpp"
#include "edit/operators/geometry/lens_calib_op.hpp"
#include "edit/operators/raw/raw_decode_op.hpp"
#include "edit/operators/wheel/color_wheel_op.hpp"

namespace alcedo {

namespace {
// Runs once to populate the factory. Uses a function-local static to be
// self-initializing on first access.
bool RegisterAllOperators() {
  auto& f = OperatorFactory::Instance();
  auto reg = [&](OperatorType type, const char* name, auto factory_fn) {
    f.Register(type, std::string(name),
               [factory_fn]() -> std::shared_ptr<IOperatorBase> {
                 return std::make_shared<std::decay_t<decltype(*factory_fn())>>(*factory_fn());
               });
  };
  // The above closure copies a default-constructed instance; simpler: register
  // lambdas that return a fresh shared_ptr.
  auto& factory = OperatorFactory::Instance();
  factory.Register(OperatorType::EXPOSURE,     "exposure",      [] { return std::make_shared<ExposureOp>(); });
  factory.Register(OperatorType::CONTRAST,     "contrast",      [] { return std::make_shared<ContrastOp>(); });
  factory.Register(OperatorType::WHITE,        "white",         [] { return std::make_shared<WhiteOp>(); });
  factory.Register(OperatorType::BLACK,        "black",         [] { return std::make_shared<BlackOp>(); });
  factory.Register(OperatorType::SHADOWS,      "shadows",       [] { return std::make_shared<ShadowOp>(); });
  factory.Register(OperatorType::HIGHLIGHTS,   "highlights",    [] { return std::make_shared<HighlightOp>(); });
  factory.Register(OperatorType::COLOR_TEMP,   "color_temp",    [] { return std::make_shared<ColorTempOp>(); });
  factory.Register(OperatorType::ACES_TONE_MAPPING, "hs_local_tone", [] { return std::make_shared<HsLocalToneVulkanOp>(); });
  factory.Register(OperatorType::TINT,         "tint",          [] { return std::make_shared<TintOp>(); });
  factory.Register(OperatorType::SATURATION,   "saturation",    [] { return std::make_shared<SaturationOp>(); });
  factory.Register(OperatorType::VIBRANCE,     "vibrance",      [] { return std::make_shared<VibranceOp>(); });
  factory.Register(OperatorType::HLS,          "hls",           [] { return std::make_shared<HlsOp>(); });
  factory.Register(OperatorType::CURVE,        "curve",         [] { return std::make_shared<CurveOp>(); });
  factory.Register(OperatorType::CST,          "cst",           [] { return std::make_shared<CstOp>(); });
  factory.Register(OperatorType::LMT,          "lmt",           [] { return std::make_shared<LmtOp>(); });
  factory.Register(OperatorType::ODT,          "odt",           [] { return std::make_shared<OdtOp>(); });
  factory.Register(OperatorType::FILM_GRAIN,   "film_grain",    [] { return std::make_shared<FilmGrainOp>(); });
  factory.Register(OperatorType::HALATION,     "halation",      [] { return std::make_shared<HalationOp>(); });
  factory.Register(OperatorType::CLARITY,      "clarity",       [] { return std::make_shared<ClarityOp>(); });
  factory.Register(OperatorType::SHARPEN,      "sharpen",       [] { return std::make_shared<SharpenOp>(); });
  factory.Register(OperatorType::RESIZE,       "resize",        [] { return std::make_shared<ResizeOp>(); });
  factory.Register(OperatorType::CROP_ROTATE,  "crop_rotate",   [] { return std::make_shared<CropRotateOp>(); });
  factory.Register(OperatorType::LENS_CALIBRATION, "lens_calib", [] { return std::make_shared<LensCalibOp>(); });
  factory.Register(OperatorType::RAW_DECODE,   "raw_decode",    [] { return std::make_shared<RawDecodeOp>(); });
  factory.Register(OperatorType::COLOR_WHEEL,  "color_wheel",   [] { return std::make_shared<ColorWheelOp>(); });
  (void)reg;  // suppress unused warning from the helper above
  return true;
}

const bool kRegistered = RegisterAllOperators();
}  // namespace

// External entry point so the JNI layer can force registration on library load
// even if no other code has touched the factory yet.
extern "C" void AlcedoRegisterOperators() {
  (void)kRegistered;
  (void)RegisterAllOperators();
}

}  // namespace alcedo

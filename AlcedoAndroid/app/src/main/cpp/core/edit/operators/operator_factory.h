#pragma once

#include "operator_base.h"
#include "color_temp_op.h"
#include "hls_op.h"
#include "curve_op.h"
#include "cst_op.h"
#include "odt_op.h"
#include "lmt_op.h"
#include "cv_cvt_op.h"
#include "raw_decode_op.h"
#include <functional>
#include <memory>
#include <unordered_map>

namespace alcedo {

class OperatorFactory {
public:
    using CreatorFunc = std::function<std::unique_ptr<IOperatorBase>()>;

    static OperatorFactory& Instance();

    bool Register(OperatorType type, CreatorFunc creator);
    std::unique_ptr<IOperatorBase> Create(OperatorType type) const;

private:
    OperatorFactory() = default;
    std::unordered_map<OperatorType, CreatorFunc> creators_;
};

template<typename T>
class OperatorRegistrar {
public:
    explicit OperatorRegistrar(OperatorType type) {
        OperatorFactory::Instance().Register(type, []() -> std::unique_ptr<IOperatorBase> {
            return std::make_unique<T>();
        });
    }
};

#define REGISTER_OPERATOR(ClassName, OpType) \
    static ::alcedo::OperatorRegistrar<ClassName> _registrar_##ClassName(OpType);

// Register all operator types with the factory
namespace {
REGISTER_OPERATOR(ColorTempOp, OperatorType::COLOR_TEMP)
REGISTER_OPERATOR(HLSOp, OperatorType::HLS)
REGISTER_OPERATOR(CurveOp, OperatorType::CURVE)
REGISTER_OPERATOR(CSTOp, OperatorType::CST)
REGISTER_OPERATOR(ODTOp, OperatorType::ODT)
REGISTER_OPERATOR(LMTOp, OperatorType::LMT)
REGISTER_OPERATOR(CVCvtColorOp, OperatorType::CV_CVT)
REGISTER_OPERATOR(RawDecodeOp, OperatorType::RAW_DECODE)
} // namespace

} // namespace alcedo

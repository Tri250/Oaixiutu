#pragma once
#include "operator_base.h"

namespace alcedo {

// Color Space Transform (CST) - ACES pipeline
// Replaces desktop's OCIO dependency with native 3D LUT implementation
// Supports: IDT (Input Device Transform), ODT (Output Device Transform)
enum class CSTTransformType : int { TO_WORKING_SPACE = 0, TO_OUTPUT_SPACE = 1 };

class CSTOp : public OperatorBase<CSTOp> {
public:
    CSTOp();
    CSTOp(CSTTransformType type, const char* input_space, const char* output_space);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::CST; }

    void SetTransformType(CSTTransformType type) { type_ = type; dirty_ = true; }
    void SetInputSpace(const char* space);
    void SetOutputSpace(const char* space);

private:
    CSTTransformType type_ = CSTTransformType::TO_WORKING_SPACE;
    char input_space_[64] = "sRGB";
    char output_space_[64] = "ACES AP1";
    float matrix_[9] = {1,0,0, 0,1,0, 0,0,1};
    bool dirty_ = true;

    void RebuildMatrix();
};

} // namespace alcedo

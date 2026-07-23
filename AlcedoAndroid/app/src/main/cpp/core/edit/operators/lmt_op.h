#pragma once
#include "operator_base.h"
#include <string>
#include <vector>

namespace alcedo {

// Look Modify Transform (LMT) - .cube LUT file application
// Ported from desktop lmt_op.cpp - replaces OCIO with native .cube parser
class LMTOp : public OperatorBase<LMTOp> {
public:
    LMTOp();
    explicit LMTOp(const char* lut_path);

    void ApplyImpl(float* pixels, int width, int height, int channels, float intensity = 1.0f);
    OperatorType GetTypeImpl() const { return OperatorType::LMT; }

    bool LoadLUT(const char* path);
    void SetLUTPath(const char* path) { lut_path_ = path; }
    bool IsLoaded() const { return lut_loaded_; }

private:
    std::string lut_path_;
    bool lut_loaded_ = false;
    int lut_size_ = 0; // e.g., 33 for 33x33x33 LUT
    std::vector<float> lut_data_; // RGB triplets

    // Trilinear interpolation
    void SampleLUT(float r, float g, float b, float* out_r, float* out_g, float* out_b) const;
    // Parse .cube file
    bool ParseCubeFile(const char* path);
};

} // namespace alcedo

#include "cst_op.h"
#include "../color_science.h"
#include <cstring>
#include <algorithm>

namespace alcedo {

CSTOp::CSTOp() {
    RebuildMatrix();
}

CSTOp::CSTOp(CSTTransformType type, const char* input_space, const char* output_space)
    : type_(type) {
    SetInputSpace(input_space);
    SetOutputSpace(output_space);
    RebuildMatrix();
}

void CSTOp::SetInputSpace(const char* space) {
    if (!space) {
        input_space_[0] = '\0';
        dirty_ = true;
        return;
    }
    std::strncpy(input_space_, space, sizeof(input_space_) - 1);
    input_space_[sizeof(input_space_) - 1] = '\0';
    dirty_ = true;
}

void CSTOp::SetOutputSpace(const char* space) {
    if (!space) {
        output_space_[0] = '\0';
        dirty_ = true;
        return;
    }
    std::strncpy(output_space_, space, sizeof(output_space_) - 1);
    output_space_[sizeof(output_space_) - 1] = '\0';
    dirty_ = true;
}

// Map space name to color_science space code
// 0=sRGB, 1=Display P3, 2=Rec2020, 3=ACES AP0, 4=ACES AP1
static int SpaceNameToCode(const char* name) {
    if (!name) return 0; // Default to sRGB for null input
    if (std::strstr(name, "sRGB") || std::strstr(name, "srgb") || std::strstr(name, "709"))
        return 0;
    if (std::strstr(name, "P3") || std::strstr(name, "p3") || std::strstr(name, "Display"))
        return 1;
    if (std::strstr(name, "2020") || std::strstr(name, "Rec2020") || std::strstr(name, "rec2020"))
        return 2;
    if (std::strstr(name, "AP0") || std::strstr(name, "ap0") || std::strstr(name, "2065"))
        return 3;
    if (std::strstr(name, "AP1") || std::strstr(name, "ap1") || std::strstr(name, "ACEScg"))
        return 4;
    // Default to sRGB
    return 0;
}

void CSTOp::RebuildMatrix() {
    int src = SpaceNameToCode(input_space_);
    int dst = SpaceNameToCode(output_space_);

    if (src == dst) {
        // Identity
        matrix_[0] = 1; matrix_[1] = 0; matrix_[2] = 0;
        matrix_[3] = 0; matrix_[4] = 1; matrix_[5] = 0;
        matrix_[6] = 0; matrix_[7] = 0; matrix_[8] = 1;
        dirty_ = false;
        return;
    }

    // Build the conversion matrix using color_science
    // We need: src → XYZ → dst
    float to_xyz[9], from_xyz[9];

    // Source → XYZ
    switch (src) {
        case 0: color_science::get_srgb_to_xyz_matrix(to_xyz); break;
        case 1: color_science::get_display_p3_to_xyz_matrix(to_xyz); break;
        case 2: color_science::get_rec2020_to_xyz_matrix(to_xyz); break;
        case 3: color_science::get_aces_ap0_to_xyz_matrix(to_xyz); break;
        case 4: {
            float ap1_to_ap0[9], ap0_to_xyz[9];
            color_science::get_aces_ap1_to_ap0_matrix(ap1_to_ap0);
            color_science::get_aces_ap0_to_xyz_matrix(ap0_to_xyz);
            // Multiply: to_xyz = ap0_to_xyz * ap1_to_ap0
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 3; ++j) {
                    to_xyz[i * 3 + j] = 0.0f;
                    for (int k = 0; k < 3; ++k) {
                        to_xyz[i * 3 + j] += ap0_to_xyz[i * 3 + k] * ap1_to_ap0[k * 3 + j];
                    }
                }
            }
            break;
        }
        default: color_science::get_srgb_to_xyz_matrix(to_xyz); break;
    }

    // XYZ → Destination
    switch (dst) {
        case 0: color_science::get_xyz_to_srgb_matrix(from_xyz); break;
        case 1: color_science::get_xyz_to_display_p3_matrix(from_xyz); break;
        case 2: color_science::get_xyz_to_rec2020_matrix(from_xyz); break;
        case 3: color_science::get_xyz_to_aces_ap0_matrix(from_xyz); break;
        case 4: {
            float xyz_to_ap0[9], ap0_to_ap1[9];
            color_science::get_xyz_to_aces_ap0_matrix(xyz_to_ap0);
            color_science::get_aces_ap0_to_ap1_matrix(ap0_to_ap1);
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 3; ++j) {
                    from_xyz[i * 3 + j] = 0.0f;
                    for (int k = 0; k < 3; ++k) {
                        from_xyz[i * 3 + j] += ap0_to_ap1[i * 3 + k] * xyz_to_ap0[k * 3 + j];
                    }
                }
            }
            break;
        }
        default: color_science::get_xyz_to_srgb_matrix(from_xyz); break;
    }

    // Compose: matrix_ = from_xyz * to_xyz
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            matrix_[i * 3 + j] = 0.0f;
            for (int k = 0; k < 3; ++k) {
                matrix_[i * 3 + j] += from_xyz[i * 3 + k] * to_xyz[k * 3 + j];
            }
        }
    }

    dirty_ = false;
}

void CSTOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    if (dirty_) {
        RebuildMatrix();
    }

    size_t total = static_cast<size_t>(width) * height;
    color_science::apply_matrix_3x3_bulk(matrix_, pixels, static_cast<int>(total), channels);
}

} // namespace alcedo

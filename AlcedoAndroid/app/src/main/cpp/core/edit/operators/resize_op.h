#pragma once

#include <cstddef>

namespace alcedo {

class ResizeOperator {
public:
    enum class Method {
        NEAREST = 0,
        BILINEAR = 1
    };

    static void resize(float* src, int src_w, int src_h,
                       float* dst, int dst_w, int dst_h,
                       int channels, int method);
};

} // namespace alcedo

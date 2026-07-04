#include <android/log.h>
#include <vector>
#include <cmath>

#define LOG_TAG "AlcedoPipeline"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

class PipelineService {
public:
    void apply_color_matrix(std::vector<float>& pixels, int width, int height,
                            const float* matrix_3x3) {
        for (int i = 0; i < width * height * 3; i += 3) {
            float r = pixels[i];
            float g = pixels[i + 1];
            float b = pixels[i + 2];
            pixels[i]     = r * matrix_3x3[0] + g * matrix_3x3[1] + b * matrix_3x3[2];
            pixels[i + 1] = r * matrix_3x3[3] + g * matrix_3x3[4] + b * matrix_3x3[5];
            pixels[i + 2] = r * matrix_3x3[6] + g * matrix_3x3[7] + b * matrix_3x3[8];
        }
    }
};

} // namespace alcedo

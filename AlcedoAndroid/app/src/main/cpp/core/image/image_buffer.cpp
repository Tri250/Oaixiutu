#include <cstdint>
#include <vector>

namespace alcedo {

struct ImageBuffer {
    int width = 0;
    int height = 0;
    int channels = 4;
    std::vector<uint8_t> cpu_data;
    bool gpu_data_valid = false;
    bool buffer_valid = false;

    void allocate(int w, int h, int c = 4) {
        width = w;
        height = h;
        channels = c;
        cpu_data.resize(w * h * c);
        buffer_valid = true;
    }
};

} // namespace alcedo

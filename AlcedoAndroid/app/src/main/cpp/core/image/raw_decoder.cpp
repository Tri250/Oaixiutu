#include <android/log.h>
#include <string>

#define LOG_TAG "AlcedoRaw"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

class RawDecoder {
public:
    bool decode(const std::string& path, int demosaic, bool highlight_reconstruction) {
        LOGI("RawDecoder::decode path=%s demosaic=%d", path.c_str(), demosaic);
        // Placeholder: integrate libraw here for actual RAW decoding
        return false;
    }
};

} // namespace alcedo

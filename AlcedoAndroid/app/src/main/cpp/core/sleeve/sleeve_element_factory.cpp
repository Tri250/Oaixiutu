#include "sleeve_element_factory.h"
#include "sleeve_file.h"
#include "sleeve_folder.h"

#include <android/log.h>

#define LOG_TAG "AlcedoSleeveFactory"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

std::shared_ptr<SleeveElement> SleeveElementFactory::CreateElement(
    uint32_t type, uint32_t id, const std::string& name) {
    switch (type) {
    case SleeveElement::TYPE_FILE: {
        auto file = std::make_shared<SleeveFile>(id, name);
        LOGI("Created SleeveFile: id=%u name=%s", id, name.c_str());
        return file;
    }
    case SleeveElement::TYPE_FOLDER: {
        auto folder = std::make_shared<SleeveFolder>(id, name);
        folder->children_loaded = true;
        LOGI("Created SleeveFolder: id=%u name=%s", id, name.c_str());
        return folder;
    }
    default:
        LOGE("Unknown element type=%u for id=%u name=%s", type, id, name.c_str());
        return nullptr;
    }
}

} // namespace alcedo

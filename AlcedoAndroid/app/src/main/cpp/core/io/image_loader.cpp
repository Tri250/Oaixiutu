// Ported from AlcedoStudio desktop: io/image_loader.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Image loader implementation for Android.
// Uses std::ifstream for file reading. No Qt, no OpenCV.

#include "io/image_loader.h"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

namespace alcedo {

namespace {

constexpr const char* kTag = "AlcedoLoader";

ImageType DetectImageTypeFromExtension(const std::string& path) {
    // Extract extension as lowercase
    auto dot = path.rfind('.');
    if (dot == std::string::npos) return ImageType::UNKNOWN;
    std::string ext = path.substr(dot + 1);
    // Lowercase
    for (auto& c : ext) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));

    if (ext == "jpg" || ext == "jpeg")  return ImageType::JPEG;
    if (ext == "png")                    return ImageType::PNG;
    if (ext == "tif" || ext == "tiff")   return ImageType::TIFF;
    if (ext == "webp")                   return ImageType::WEBP;
    if (ext == "dng")                    return ImageType::DNG;
    if (ext == "heif" || ext == "heic")  return ImageType::HEIF;

    // RAW formats
    if (ext == "nef" || ext == "cr2" || ext == "cr3" || ext == "arw" ||
        ext == "raf" || ext == "orf" || ext == "rw2" || ext == "pef" ||
        ext == "srw" || ext == "nrw" || ext == "x3f" || ext == "raw" ||
        ext == "sr2" || ext == "kdc" || ext == "dcr" || ext == "erf" ||
        ext == "mrw" || ext == "3fr" || ext == "fff" || ext == "iiq") {
        return ImageType::RAW;
    }

    return ImageType::UNKNOWN;
}

DecodeTaskType ToDecodeTaskType(DecodeType type) {
    switch (type) {
        case DecodeType::SLEEVE_LOADING: return DecodeTaskType::METADATA;
        case DecodeType::THUMB:          return DecodeTaskType::THUMBNAIL;
        case DecodeType::FULL:           return DecodeTaskType::FULL_DECODE;
    }
    return DecodeTaskType::METADATA;
}

DecodeTaskPriority ToDecodePriority(DecodeType type) {
    switch (type) {
        case DecodeType::SLEEVE_LOADING: return DecodeTaskPriority::HIGH;
        case DecodeType::THUMB:          return DecodeTaskPriority::MEDIUM;
        case DecodeType::FULL:           return DecodeTaskPriority::LOW;
    }
    return DecodeTaskPriority::MEDIUM;
}

} // anonymous namespace

// ============================================================
// ImageLoader
// ============================================================

ImageLoader::ImageLoader() {
    scheduler_.set_thread_count(2);
    scheduler_.start();
}

ImageLoader::~ImageLoader() {
    scheduler_.stop();
}

void ImageLoader::StartLoading(const std::vector<std::string>& paths,
                                DecodeType type,
                                DecodeCompleteFn complete_fn) {
    if (complete_fn) {
        scheduler_.set_complete_callback(std::move(complete_fn));
    }
    for (const auto& path : paths) {
        switch (ToDecodeTaskType(type)) {
            case DecodeTaskType::METADATA:
                scheduler_.submit_metadata(path);
                break;
            case DecodeTaskType::THUMBNAIL:
                scheduler_.submit_thumbnail(path, nullptr, ToDecodePriority(type));
                break;
            case DecodeTaskType::FULL_DECODE:
                scheduler_.submit_full_decode(path, nullptr, ToDecodePriority(type));
                break;
            default:
                break;
        }
    }
}

uint64_t ImageLoader::StartLoading(const std::string& path,
                                    DecodeType type,
                                    DecodeTask::Callback on_complete) {
    auto priority = ToDecodePriority(type);
    switch (ToDecodeTaskType(type)) {
        case DecodeTaskType::METADATA:
            return scheduler_.submit_metadata(path, std::move(on_complete), priority);
        case DecodeTaskType::THUMBNAIL:
            return scheduler_.submit_thumbnail(path, std::move(on_complete), priority);
        case DecodeTaskType::FULL_DECODE:
            return scheduler_.submit_full_decode(path, std::move(on_complete), priority);
        default:
            return 0;
    }
}

std::shared_ptr<Image> ImageLoader::LoadImage(const std::string& path,
                                               DecodeType type) {
    namespace fs = std::filesystem;

    if (!fs::exists(path)) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "LoadImage: file not found: %s", path.c_str());
        return nullptr;
    }

    auto img = std::make_shared<Image>(0, path);
    img->SetImageType(DetectImageTypeFromExtension(path));

    // Load raw bytes from the file
    auto bytes = ByteBufferLoader::LoadByteBufferFromPath(path);
    if (bytes.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "LoadImage: failed to read: %s", path.c_str());
        return nullptr;
    }

    if (type == DecodeType::FULL || type == DecodeType::THUMB) {
        ImageBuffer buf;
        // Store the raw bytes as cpu_data for later decoding.
        // The actual pixel decode is done by DecoderScheduler workers;
        // here we just set up the byte buffer so the Image carries the file data.
        buf.cpu_data = std::move(bytes);
        buf.is_raw = (img->GetImageType() == ImageType::RAW ||
                      img->GetImageType() == ImageType::DNG);
        img->LoadOriginalData(std::move(buf));
        img->ComputeChecksum();
    }

    __android_log_print(ANDROID_LOG_DEBUG, kTag,
                        "LoadImage: loaded %s (type=%d)",
                        path.c_str(), static_cast<int>(img->GetImageType()));
    return img;
}

bool ImageLoader::CancelTask(uint64_t task_id) {
    return scheduler_.cancel_task(task_id);
}

void ImageLoader::CancelAll() {
    scheduler_.cancel_all();
}

ImageType ImageLoader::DetectImageType(const std::string& path) {
    return DetectImageTypeFromExtension(path);
}

// ============================================================
// ByteBufferLoader
// ============================================================

std::vector<uint8_t> ByteBufferLoader::LoadByteBufferFromPath(const std::string& path) {
    std::ifstream ifs(path, std::ios::binary | std::ios::ate);
    if (!ifs.is_open()) {
        __android_log_print(ANDROID_LOG_WARN, "AlcedoLoader",
                            "LoadByteBufferFromPath: cannot open: %s", path.c_str());
        return {};
    }

    auto size = ifs.tellg();
    if (size <= 0) {
        return {};
    }

    ifs.seekg(0, std::ios::beg);
    std::vector<uint8_t> buffer(static_cast<size_t>(size));
    if (!ifs.read(reinterpret_cast<char*>(buffer.data()), static_cast<std::streamsize>(size))) {
        __android_log_print(ANDROID_LOG_WARN, "AlcedoLoader",
                            "LoadByteBufferFromPath: read failed: %s", path.c_str());
        return {};
    }

    return buffer;
}

} // namespace alcedo

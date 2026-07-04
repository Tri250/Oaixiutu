// Ported from AlcedoStudio desktop: io/export_icc_profile_resolver.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// ICC profile resolver implementation for Android.
// On Android, ICC profiles come from bundled assets or the app's
// internal data directory. No Apple CoreGraphics, no Windows API.

#include "io/export_icc_profile_resolver.h"

#include <android/log.h>
#include <filesystem>
#include <fstream>
#include <vector>

namespace alcedo {

namespace {

constexpr const char* kTag = "AlcedoICC";

} // anonymous namespace

std::string ExportIccProfileResolver::assets_directory_;

// ============================================================
// Public API
// ============================================================

std::string ExportIccProfileResolver::ResolveIccProfile(int encoding_space,
                                                         int encoding_eotf) {
    std::string filename = BuildIccFilename(encoding_space, encoding_eotf);
    if (filename.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "ResolveIccProfile: no profile for space=%d eotf=%d",
                            encoding_space, encoding_eotf);
        return {};
    }

    auto search_paths = GetSearchPaths();
    for (const auto& dir : search_paths) {
        std::string full_path = dir;
        if (!full_path.empty() && full_path.back() != '/') {
            full_path += '/';
        }
        full_path += filename;

        if (std::filesystem::exists(full_path)) {
            __android_log_print(ANDROID_LOG_DEBUG, kTag,
                                "ResolveIccProfile: found %s", full_path.c_str());
            return full_path;
        }
    }

    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "ResolveIccProfile: %s not found in any search path",
                        filename.c_str());
    return {};
}

std::vector<uint8_t> ExportIccProfileResolver::LoadIccProfileData(int encoding_space,
                                                                    int encoding_eotf) {
    std::string path = ResolveIccProfile(encoding_space, encoding_eotf);
    if (path.empty()) return {};

    std::ifstream ifs(path, std::ios::binary | std::ios::ate);
    if (!ifs.is_open()) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "LoadIccProfileData: cannot open %s", path.c_str());
        return {};
    }

    auto size = ifs.tellg();
    if (size <= 0) return {};

    ifs.seekg(0, std::ios::beg);
    std::vector<uint8_t> data(static_cast<size_t>(size));
    if (!ifs.read(reinterpret_cast<char*>(data.data()),
                  static_cast<std::streamsize>(size))) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "LoadIccProfileData: read failed %s", path.c_str());
        return {};
    }

    return data;
}

void ExportIccProfileResolver::SetAssetsDirectory(const std::string& path) {
    assets_directory_ = path;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "SetAssetsDirectory: %s", path.c_str());
}

const std::string& ExportIccProfileResolver::GetAssetsDirectory() {
    return assets_directory_;
}

const char* ExportIccProfileResolver::EncodingSpaceName(int encoding_space) {
    switch (encoding_space) {
        case 0: return "sRGB";
        case 1: return "DisplayP3";
        case 2: return "Rec2020";
        case 3: return "ACES AP1";
        default: return "Unknown";
    }
}

const char* ExportIccProfileResolver::EncodingEotfName(int encoding_eotf) {
    switch (encoding_eotf) {
        case 0: return "sRGB";
        case 1: return "PQ";
        case 2: return "HLG";
        case 3: return "Linear";
        case 4: return "Gamma2.2";
        case 5: return "Gamma2.4";
        default: return "Unknown";
    }
}

// ============================================================
// Private
// ============================================================

std::string ExportIccProfileResolver::BuildIccFilename(int encoding_space,
                                                        int encoding_eotf) {
    // Naming convention: alcedo_<space>_<eotf>.icc
    const char* space_name = nullptr;
    switch (encoding_space) {
        case 0: space_name = "srgb"; break;
        case 1: space_name = "display_p3"; break;
        case 2: space_name = "rec2020"; break;
        case 3: space_name = "aces_ap1"; break;
        default: return {};
    }

    const char* eotf_name = nullptr;
    switch (encoding_eotf) {
        case 0: eotf_name = "srgb_trc"; break;
        case 1: eotf_name = "pq"; break;
        case 2: eotf_name = "hlg"; break;
        case 3: eotf_name = "linear"; break;
        case 4: eotf_name = "gamma22"; break;
        case 5: eotf_name = "gamma24"; break;
        default: return {};
    }

    return std::string("alcedo_") + space_name + "_" + eotf_name + ".icc";
}

std::vector<std::string> ExportIccProfileResolver::GetSearchPaths() {
    std::vector<std::string> paths;

    // 1. App assets directory (set via SetAssetsDirectory)
    if (!assets_directory_.empty()) {
        paths.push_back(assets_directory_ + "/icc_profiles");
        paths.push_back(assets_directory_);
    }

    // 2. Android assets (app's internal data directory / files)
    // These are typically accessible via Context.getFilesDir() on the Kotlin side.
    // The C++ side receives this via SetAssetsDirectory.

    // 3. System-level ICC profiles (may exist on some Android versions)
    paths.push_back("/system/usr/share/icc_profiles");
    paths.push_back("/vendor/etc/icc_profiles");

    return paths;
}

} // namespace alcedo

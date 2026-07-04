// Ported from AlcedoStudio desktop: io/export_icc_profile_resolver.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// ICC profile resolver for Android. On Android, ICC profiles come from
// Android's ColorSpace API or from bundled assets. The C++ resolver maps
// ColorSpace+EOTF combinations to ICC asset file names and search paths.
// No Apple CoreGraphics, no Windows API.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace alcedo {

// ============================================================
// ICC Profile resolver
// ============================================================
class ExportIccProfileResolver {
public:
    // Resolve the ICC profile asset path for the given encoding space and EOTF.
    // encoding_space: 0=sRGB, 1=DisplayP3, 2=Rec2020, 3=ACES AP1
    // encoding_eotf:  0=sRGB, 1=PQ, 2=HLG, 3=Linear, 4=Gamma2.2, 5=Gamma2.4
    // Returns the full path to the ICC profile file if found, or empty string.
    static std::string ResolveIccProfile(int encoding_space, int encoding_eotf);

    // Get the ICC profile file data. Returns empty vector if not found.
    static std::vector<uint8_t> LoadIccProfileData(int encoding_space, int encoding_eotf);

    // Set the assets directory path (e.g. from Android Context.getFilesDir()).
    // Must be called once at app init before any Resolve/Load calls.
    static void SetAssetsDirectory(const std::string& path);

    // Get the current assets directory.
    static const std::string& GetAssetsDirectory();

    // Map encoding_space to a human-readable name.
    static const char* EncodingSpaceName(int encoding_space);

    // Map encoding_eotf to a human-readable name.
    static const char* EncodingEotfName(int encoding_eotf);

private:
    // Build the ICC profile filename from space + eotf.
    static std::string BuildIccFilename(int encoding_space, int encoding_eotf);

    // Search paths for ICC profiles (assets dir + system paths).
    static std::vector<std::string> GetSearchPaths();

    static std::string assets_directory_;
};

} // namespace alcedo

// AlcedoAndroid - type aliases for the native core.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <filesystem>
#include <string>

namespace alcedo {

// Path / name aliases (kept compatible with the desktop project's vocabulary).
using image_path_t = std::filesystem::path;
using file_path_t   = std::filesystem::path;
using file_name_t   = std::string;
using sl_path_t     = std::string;

using image_id_t      = uint32_t;
using sleeve_id_t     = uint32_t;
using sl_element_id_t = uint32_t;
using filter_id_t     = uint32_t;
using p_hash_t        = uint64_t;
using frame_id_t      = uint32_t;
using request_id_t    = size_t;
using tx_id_t         = uint64_t;
using PriorityLevel   = int;

// Decode resolution hint used by the decoder scheduler.
enum class DecodeRes { FULL, HALF, QUARTER, EIGHTH };

}  // namespace alcedo

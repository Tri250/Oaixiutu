// Ported from AlcedoStudio desktop: utils/import/import_log.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Adapted for Android: std::filesystem::path is replaced with std::string
// (Android paths flow through SAF/MediaStore URIs and plain string paths),
// and the id types are pinned to the same aliases used elsewhere in the
// Android native core (uint32_t element id, uint64_t image id).

#pragma once

#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "import_error_code.h"

namespace alcedo {

using image_id_t     = uint64_t;
using sl_element_id_t = uint32_t;
using file_name_t    = std::string;

struct ImportLogEntry {
    image_id_t        image_id_      = 0;
    sl_element_id_t   element_id_    = 0;
    file_name_t       file_name_{};
    std::string       source_path_{};
    ImportErrorCode   error_code_    = ImportErrorCode::UNKNOWN;
    std::string       error_message_{};
    bool              metadata_ok_   = false;
};

struct ImportLogSnapshot {
    std::vector<ImportLogEntry> created_{};
    std::vector<image_id_t>     metadata_ok_{};
    std::vector<ImportLogEntry> metadata_failed_{};
    std::vector<ImportLogEntry> unsupported_nikon_he_{};
};

class ImportLog {
public:
    void AddPlaceholder(const image_id_t image_id, const sl_element_id_t element_id,
                        const file_name_t& file_name, const std::string& source_path) {
        std::lock_guard<std::mutex> lock(mtx_);
        ImportLogEntry entry;
        entry.image_id_    = image_id;
        entry.element_id_  = element_id;
        entry.file_name_   = file_name;
        entry.source_path_ = source_path;
        entries_[image_id] = entry;
    }

    void MarkMetadataSuccess(const image_id_t image_id) {
        std::lock_guard<std::mutex> lock(mtx_);
        auto it = entries_.find(image_id);
        if (it == entries_.end()) {
            return;
        }
        it->second.metadata_ok_ = true;
        metadata_ok_.insert(image_id);
    }

    void MarkMetadataFailure(const image_id_t image_id, const ImportErrorCode error_code,
                             const std::string& error_message = {}) {
        std::lock_guard<std::mutex> lock(mtx_);
        auto it = entries_.find(image_id);
        if (it == entries_.end()) {
            return;
        }
        it->second.metadata_ok_    = false;
        it->second.error_code_     = error_code;
        it->second.error_message_  = error_message;
        metadata_ok_.erase(image_id);
    }

    ImportLogSnapshot Snapshot() const {
        std::lock_guard<std::mutex> lock(mtx_);
        ImportLogSnapshot snapshot;
        snapshot.created_.reserve(entries_.size());
        snapshot.metadata_ok_.reserve(metadata_ok_.size());
        snapshot.metadata_failed_.reserve(entries_.size());
        snapshot.unsupported_nikon_he_.reserve(entries_.size());

        for (const auto& [id, entry] : entries_) {
            snapshot.created_.push_back(entry);
            if (entry.metadata_ok_) {
                snapshot.metadata_ok_.push_back(id);
            } else if (entry.error_code_ == ImportErrorCode::UNSUPPORTED_NIKON_HE_RAW) {
                snapshot.unsupported_nikon_he_.push_back(entry);
            } else {
                snapshot.metadata_failed_.push_back(entry);
            }
        }
        return snapshot;
    }

private:
    mutable std::mutex                             mtx_{};
    std::unordered_map<image_id_t, ImportLogEntry> entries_{};
    std::unordered_set<image_id_t>                metadata_ok_{};
};

}  // namespace alcedo

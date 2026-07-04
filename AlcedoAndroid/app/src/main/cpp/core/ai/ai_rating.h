// Ported from AlcedoStudio desktop: ai/ai_rating.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// AI-generated *rating*: a single 1..5 integer star rating produced from a
// rendered rendition. Standalone domain object; FK is file_id (Sleeve element
// id / inode). Mirrors the desktop struct so native CLIP/ONNX code can carry
// rating results without crossing JNI; persistence goes through the Kotlin
// AiService / AiEmbeddingDao layer.

#pragma once

#include <cstdint>
#include <string>

namespace alcedo {

using sl_element_id_t = uint32_t;

struct AiRating {
    sl_element_id_t file_id_           = 0;    // FK -> Sleeve element id (inode)
    std::string     task_id_           {};     // analysis task identity (rubric run, etc.)
    std::string     provider_id_       {};
    std::string     model_id_          {};
    std::string     prompt_profile_id_ {};     // prompt/profile identity
    std::string     rendition_kind_    {};     // e.g. "thumbnail_k1024"
    int             rating_           = 0;      // 1..=5 on success; 0 = unset
    std::string     rubric_id_         {};      // which rubric produced this rating
    std::string     rubric_version_    {};      // rubric version
    std::string     reasons_           {};      // short human-readable rationale
    bool            active_            = true;  // active-for-rating flag

    // Remote LLM rating contract: 1..5 (0 means unrated / not scored).
    static constexpr int kMinRating = 1;
    static constexpr int kMaxRating = 5;
    static constexpr int NormalizeRating(int rating) {
        return rating < kMinRating ? kMinRating : (rating > kMaxRating ? kMaxRating : rating);
    }

    // Strict check: file key + provider/model identity + 1..5 rating present.
    bool IsValid() const;

    // Phase 7a reasons-only gate: rating value is ignored (the real star lives
    // in the EXIF/metadata Rating column), only identity + non-empty reasons.
    bool IsValidReasonsOnly() const;
};

}  // namespace alcedo

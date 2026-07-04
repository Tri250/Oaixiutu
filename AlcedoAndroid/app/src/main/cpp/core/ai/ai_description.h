// Ported from AlcedoStudio desktop: ai/ai_description.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// The image's own AI-generated *understanding*: caption, tags, scene/category
// hints produced from a rendered rendition. Standalone domain object; FK is
// file_id (the Sleeve element id / inode). Android stores it via the Kotlin
// AiService / AiEmbeddingDao layer, this C++ struct mirrors the desktop so
// native code (e.g. CLIP pipeline) can pass it around without crossing JNI.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace alcedo {

using sl_element_id_t = uint32_t;

struct AiDescription {
    sl_element_id_t file_id_           = 0;    // FK -> Sleeve element id (inode)
    std::string     task_id_           {};     // analysis task identity
    std::string     provider_id_       {};     // e.g. "openrouter", "volcengine_ark"
    std::string     model_id_          {};     // remote/local model that produced this
    std::string     prompt_profile_id_ {};     // prompt/profile identity (rubric-change guard)
    std::string     rendition_kind_    {};     // e.g. "thumbnail_k1024"
    std::string     caption_           {};     // free-form caption
    std::string     tags_json_         {};     // JSON array string; "" = no tags
    std::string     scene_             {};     // scene / category hint
    double          confidence_        = 0.0;  // describe-task confidence (0..1)
    bool            active_            = true; // active-for-search flag

    // Parse tags_json_ into a tag list. Returns empty list when tags_json_ is
    // empty or not a JSON string array (malformed store => "no tags", not error).
    std::vector<std::string> Tags() const;

    // Serialize a tag list into tags_json_ as a JSON array string.
    void SetTags(const std::vector<std::string>& tags);

    // Storable only when file key + provider/model identity present.
    bool IsValid() const;
};

}  // namespace alcedo

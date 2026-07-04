// Ported from AlcedoStudio desktop: ai/ai_rating.cpp
// SPDX-License-Identifier: GPL-3.0-only

#include "ai_rating.h"

namespace alcedo {

bool AiRating::IsValid() const {
    return file_id_ != 0 && !task_id_.empty() && !provider_id_.empty() &&
           !model_id_.empty() && rating_ >= kMinRating && rating_ <= kMaxRating;
}

bool AiRating::IsValidReasonsOnly() const {
    // Rating value intentionally ignored: a 7a reasons row carries rating_ = 0
    // as a sentinel because the real star lives in the EXIF/metadata Rating
    // column. Only the file key, provider/model identity, and non-empty reasons.
    return file_id_ != 0 && !task_id_.empty() && !provider_id_.empty() &&
           !model_id_.empty() && !reasons_.empty();
}

}  // namespace alcedo

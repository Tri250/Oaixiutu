// AlcedoAndroid - SearchQueryClassifier implementation.
// Classifies natural-language search queries into filter types and SQL.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>
#include <cctype>

namespace alcedo {

static auto to_lower(const std::string& s) -> std::string {
  std::string out = s;
  std::transform(out.begin(), out.end(), out.begin(),
                 [](unsigned char c) { return std::tolower(c); });
  return out;
}

auto SearchQueryClassifier::Classify(const std::string& query) -> QueryType {
  auto q = to_lower(query);
  if (q.empty()) return QueryType::UNKNOWN;
  // Date-related keywords.
  if (q.find("date") != std::string::npos || q.find("time") != std::string::npos ||
      q.find("year") != std::string::npos || q.find("month") != std::string::npos)
    return QueryType::DATE;
  // EXIF-related keywords.
  if (q.find("camera") != std::string::npos || q.find("lens") != std::string::npos ||
      q.find("iso") != std::string::npos || q.find("aperture") != std::string::npos ||
      q.find("focal") != std::string::npos || q.find("shutter") != std::string::npos)
    return QueryType::EXIF;
  // Rating.
  if (q.find("rating") != std::string::npos || q.find("star") != std::string::npos ||
      q.find("rated") != std::string::npos)
    return QueryType::RATING;
  // Semantic tags.
  if (q.find("tag:") != std::string::npos || q.find("semantic:") != std::string::npos ||
      q.find("label:") != std::string::npos)
    return QueryType::SEMANTIC;
  return QueryType::TEXT;
}

auto SearchQueryClassifier::ToSqlFilter(const std::string& query) -> std::string {
  auto type = Classify(query);
  switch (type) {
    case QueryType::TEXT:
      return "file_name LIKE '%" + query + "%'";
    case QueryType::DATE:
      return "capture_time IS NOT NULL";
    case QueryType::EXIF:
      return "exif_camera_model IS NOT NULL";
    case QueryType::RATING:
      return "rating > 0";
    case QueryType::SEMANTIC: {
      // Extract tag value after "tag:" / "label:" / "semantic:".
      auto pos = query.find(':');
      if (pos != std::string::npos) {
        std::string tag = query.substr(pos + 1);
        return "semantic_tags LIKE '%" + tag + "%'";
      }
      return "1=1";
    }
    default:
      return "1=1";
  }
}

}  // namespace alcedo

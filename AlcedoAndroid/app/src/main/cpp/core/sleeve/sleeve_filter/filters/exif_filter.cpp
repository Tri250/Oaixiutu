// AlcedoAndroid - ExifFilter implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "exif_filter.hpp"

#include <cstdio>
#include <sstream>
#include <string>

namespace alcedo {

void ExifFilter::SetFilter(FilterableMetadata metadata, ElementOrder order) {
  metadata_ = metadata;
  order_    = order;
}

void ExifFilter::SetValue(FilterableMetadata value) {
  metadata_ = value;
}

void ExifFilter::ResetFilter() {
  metadata_ = FilterableMetadata{};
  order_    = ElementOrder::ASC;
}

auto ExifFilter::GetPredicate() -> std::string {
  std::ostringstream oss;
  bool first = true;
  auto add = [&](const std::string& clause) {
    if (!first) oss << " AND ";
    oss << clause;
    first = false;
  };
  if (!metadata_.make_.empty()) {
    add("exif_camera_make = '" + metadata_.make_ + "'");
  }
  if (!metadata_.model_.empty()) {
    add("exif_camera_model = '" + metadata_.model_ + "'");
  }
  if (!metadata_.lens_.empty()) {
    add("exif_lens = '" + metadata_.lens_ + "'");
  }
  if (!metadata_.lens_make_.empty()) {
    add("exif_lens_make = '" + metadata_.lens_make_ + "'");
  }
  if (metadata_.aperture_ > 0.0f) {
    char buf[64];
    std::snprintf(buf, sizeof(buf), "exif_aperture >= %.4f", metadata_.aperture_);
    add(buf);
  }
  if (metadata_.focal_ > 0.0f) {
    char buf[64];
    std::snprintf(buf, sizeof(buf), "exif_focal_length >= %.4f", metadata_.focal_);
    add(buf);
  }
  if (oss.str().empty()) return "1=1";
  return oss.str();
}

static auto escape_json(const std::string& s) -> std::string {
  std::string out;
  out.reserve(s.size() + 2);
  for (char c : s) {
    switch (c) {
      case '"':  out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n";  break;
      case '\r': out += "\\r";  break;
      case '\t': out += "\\t";  break;
      default:   out += c;
    }
  }
  return out;
}

auto ExifFilter::ToJSON() -> std::string {
  std::ostringstream oss;
  oss << "{\"type\":\"exif\"";
  oss << ",\"make\":\""    << escape_json(metadata_.make_)      << "\"";
  oss << ",\"model\":\""   << escape_json(metadata_.model_)     << "\"";
  oss << ",\"lens\":\""    << escape_json(metadata_.lens_)      << "\"";
  oss << ",\"lens_make\":\"" << escape_json(metadata_.lens_make_) << "\"";
  oss << ",\"aperture\":"  << metadata_.aperture_;
  oss << ",\"focal\":"     << metadata_.focal_;
  oss << ",\"order\":\""   << (order_ == ElementOrder::ASC ? "asc" : "desc") << "\"";
  oss << "}";
  return oss.str();
}

static auto extract_json_string(const std::string& j_str, const std::string& key) -> std::string {
  std::string needle = "\"" + key + "\":\"";
  auto pos = j_str.find(needle);
  if (pos == std::string::npos) return {};
  pos += needle.size();
  std::string out;
  while (pos < j_str.size() && j_str[pos] != '"') {
    if (j_str[pos] == '\\' && pos + 1 < j_str.size()) {
      ++pos;
      switch (j_str[pos]) {
        case '"':  out += '"';  break;
        case '\\': out += '\\'; break;
        case 'n':  out += '\n'; break;
        case 'r':  out += '\r'; break;
        case 't':  out += '\t'; break;
        default:   out += j_str[pos];
      }
    } else {
      out += j_str[pos];
    }
    ++pos;
  }
  return out;
}

static auto extract_json_float(const std::string& j_str, const std::string& key) -> float {
  std::string needle = "\"" + key + "\":";
  auto pos = j_str.find(needle);
  if (pos == std::string::npos) return 0.0f;
  pos += needle.size();
  return std::strtof(j_str.c_str() + pos, nullptr);
}

void ExifFilter::FromJSON(const std::string& j_str) {
  metadata_.make_      = extract_json_string(j_str, "make");
  metadata_.model_     = extract_json_string(j_str, "model");
  metadata_.lens_      = extract_json_string(j_str, "lens");
  metadata_.lens_make_ = extract_json_string(j_str, "lens_make");
  metadata_.aperture_  = extract_json_float(j_str, "aperture");
  metadata_.focal_     = extract_json_float(j_str, "focal");
  auto order = extract_json_string(j_str, "order");
  order_ = (order == "desc") ? ElementOrder::DESC : ElementOrder::ASC;
}

}  // namespace alcedo

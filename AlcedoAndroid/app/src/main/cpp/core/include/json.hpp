// AlcedoAndroid - JSON alias.
// The codebase uses nlohmann::json throughout; this header centralizes the
// include so call sites can write `#include "json.hpp"`. The build system is
// expected to provide the single-header nlohmann/json.hpp on the include path.
#pragma once

#include <nlohmann/json.hpp>

namespace alcedo {
using json = nlohmann::json;
}  // namespace alcedo

// Convenience alias used by ported operators/history code.
#define nlohmann ::nlohmann

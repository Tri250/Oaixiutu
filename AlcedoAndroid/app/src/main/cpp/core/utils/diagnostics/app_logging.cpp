// AlcedoAndroid - diagnostics translation unit for the logging header.
// SPDX-License-Identifier: GPL-3.0-only
#include "utils/app_logging.hpp"

namespace alcedo {
namespace log {

// The header provides inline functions and macros; this translation unit exists
// so the build has a dedicated object for diagnostics and future additions
// (e.g. an in-memory ring buffer or crash-report hook) can land here.
void Initialize() {
  // Android __android_log_print requires no initialization; reserved for
  // future on-device log file setup.
}

}  // namespace log
}  // namespace alcedo

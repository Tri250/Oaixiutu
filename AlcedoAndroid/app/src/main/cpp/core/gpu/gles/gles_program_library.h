#pragma once

#include "gles_compute.h"
#include <string>
#include <unordered_map>
#include <memory>
#include <mutex>

namespace alcedo {
namespace gpu {

// ============================================================
// Program entry with metadata
// ============================================================
struct GlesProgramEntry {
    std::shared_ptr<GlesComputeProgram> program;
    std::string name;
    std::string source;
    int64_t lastUsed = 0;
    int64_t compileTimeMs = 0;
    bool compiled = false;
};

// ============================================================
// Program library - caches compiled shader programs
// ============================================================
class GlesProgramLibrary {
public:
    static GlesProgramLibrary& instance() {
        static GlesProgramLibrary lib;
        return lib;
    }

    // Register a named shader source
    void registerSource(const std::string& name, const std::string& source);

    // Get or compile a program
    std::shared_ptr<GlesComputeProgram> getProgram(const std::string& name);
    std::shared_ptr<GlesComputeProgram> getProgram(const std::string& name,
                                                    const std::string& defines);

    // Check if program is cached
    bool isCached(const std::string& name) const;

    // Invalidate a specific program or all programs
    void invalidate(const std::string& name);
    void invalidateAll();

    // Release all programs
    void releaseAll();

    // Hot-reload: reload a shader from its source
    bool reload(const std::string& name);

    // Get number of cached programs
    size_t cacheSize() const { return programs_.size(); }

    // Get compilation statistics
    struct Stats {
        size_t totalPrograms = 0;
        size_t compiledPrograms = 0;
        int64_t totalCompileTimeMs = 0;
    };
    Stats getStats() const;

private:
    GlesProgramLibrary() = default;
    ~GlesProgramLibrary() { releaseAll(); }

    std::string makeKey(const std::string& name, const std::string& defines) const;

    std::unordered_map<std::string, std::string> sources_;         // name -> source
    std::unordered_map<std::string, GlesProgramEntry> programs_;   // key -> entry
    std::mutex mutex_;
};

} // namespace gpu
} // namespace alcedo
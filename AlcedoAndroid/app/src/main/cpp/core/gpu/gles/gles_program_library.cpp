#include "gles_program_library.h"
#include <chrono>

namespace alcedo {
namespace gpu {

void GlesProgramLibrary::registerSource(const std::string& name, const std::string& source) {
    std::lock_guard<std::mutex> lock(mutex_);
    sources_[name] = source;
}

std::shared_ptr<GlesComputeProgram> GlesProgramLibrary::getProgram(const std::string& name) {
    return getProgram(name, "");
}

std::shared_ptr<GlesComputeProgram> GlesProgramLibrary::getProgram(
    const std::string& name, const std::string& defines) {

    std::string key = makeKey(name, defines);

    {
        std::lock_guard<std::mutex> lock(mutex_);

        // Check cache
        auto it = programs_.find(key);
        if (it != programs_.end() && it->second.compiled && it->second.program->isValid()) {
            it->second.lastUsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
            return it->second.program;
        }

        // Find source
        auto srcIt = sources_.find(name);
        if (srcIt == sources_.end()) {
            GPU_LOGE("Program source not found: %s", name.c_str());
            return nullptr;
        }

        // Compile
        auto startTime = std::chrono::high_resolution_clock::now();

        auto program = std::make_shared<GlesComputeProgram>();
        if (!program->create(srcIt->second, defines)) {
            GPU_LOGE("Failed to compile program: %s (defines: %s)", name.c_str(), defines.c_str());
            return nullptr;
        }

        auto endTime = std::chrono::high_resolution_clock::now();
        int64_t compileMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            endTime - startTime).count();

        // Cache it
        GlesProgramEntry entry;
        entry.program = program;
        entry.name = name;
        entry.source = srcIt->second;
        entry.lastUsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
        entry.compileTimeMs = compileMs;
        entry.compiled = true;

        programs_[key] = std::move(entry);

        GPU_LOGI("Compiled program '%s' in %lld ms (defines: %s)",
                 name.c_str(), (long long)compileMs,
                 defines.empty() ? "(none)" : defines.c_str());

        return program;
    }
}

bool GlesProgramLibrary::isCached(const std::string& name) const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::string key = makeKey(name, "");
    auto it = programs_.find(key);
    return it != programs_.end() && it->second.compiled;
}

void GlesProgramLibrary::invalidate(const std::string& name) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Invalidate all variants of this program
    auto it = programs_.begin();
    while (it != programs_.end()) {
        if (it->second.name == name) {
            it = programs_.erase(it);
        } else {
            ++it;
        }
    }

    GPU_LOGI("Invalidated program cache for: %s", name.c_str());
}

void GlesProgramLibrary::invalidateAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    programs_.clear();
    GPU_LOGI("Invalidated all program caches");
}

void GlesProgramLibrary::releaseAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    programs_.clear();
    sources_.clear();
}

bool GlesProgramLibrary::reload(const std::string& name) {
    invalidate(name);
    return getProgram(name) != nullptr;
}

GlesProgramLibrary::Stats GlesProgramLibrary::getStats() const {
    std::lock_guard<std::mutex> lock(mutex_);
    Stats stats;
    stats.totalPrograms = programs_.size();
    for (const auto& [_, entry] : programs_) {
        if (entry.compiled) {
            stats.compiledPrograms++;
            stats.totalCompileTimeMs += entry.compileTimeMs;
        }
    }
    return stats;
}

std::string GlesProgramLibrary::makeKey(const std::string& name, const std::string& defines) const {
    if (defines.empty()) {
        return name;
    }
    return name + ":" + defines;
}

} // namespace gpu
} // namespace alcedo
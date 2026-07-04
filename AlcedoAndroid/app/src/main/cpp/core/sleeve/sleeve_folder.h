#pragma once

#include "sleeve_element.h"
#include <cstdint>
#include <string>
#include <map>

namespace alcedo {

class SleeveFolder : public SleeveElement {
public:
    std::map<std::string, uint32_t> contents;
    uint32_t file_count = 0;
    uint32_t folder_count = 0;
    bool children_loaded = false;

    SleeveFolder(uint32_t id, const std::string& name)
        : SleeveElement(id, name, TYPE_FOLDER),
          file_count(0), folder_count(0), children_loaded(false) {}

    void AddChild(const std::string& name, uint32_t id, bool is_file) {
        contents[name] = id;
        if (is_file) {
            ++file_count;
        } else {
            ++folder_count;
        }
    }

    void RemoveChild(const std::string& name, bool is_file) {
        contents.erase(name);
        if (is_file) {
            if (file_count > 0) --file_count;
        } else {
            if (folder_count > 0) --folder_count;
        }
    }

    bool HasChild(const std::string& name) const {
        return contents.find(name) != contents.end();
    }
};

} // namespace alcedo

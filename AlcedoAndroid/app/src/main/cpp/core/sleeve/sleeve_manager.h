#pragma once

#include "sleeve_filesystem.h"
#include <memory>

namespace alcedo {

class SleeveManager {
public:
    SleeveManager();
    ~SleeveManager();

    bool Initialize();

    std::shared_ptr<FileSystem> GetFilesystem();
    std::shared_ptr<SleeveBase> GetSleeveBase();

private:
    std::shared_ptr<SleeveBase> sleeve_base_;
    std::shared_ptr<FileSystem> filesystem_;
};

} // namespace alcedo

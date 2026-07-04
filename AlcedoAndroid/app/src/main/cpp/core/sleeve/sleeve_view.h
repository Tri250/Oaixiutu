#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>
#include "sleeve_element.h"
#include "sleeve_folder.h"

namespace alcedo {

class SleeveBase;

struct DisplayingImage {
    int image_id = 0;
    bool thumb_pinned = false;
    bool full_pinned = false;

    DisplayingImage() = default;
    explicit DisplayingImage(int id) : image_id(id), thumb_pinned(false), full_pinned(false) {}
};

using ElementResolver = std::function<std::shared_ptr<SleeveElement>(uint32_t)>;

class SleeveView {
public:
    SleeveView(std::shared_ptr<SleeveFolder> root_folder, const std::string& path);
    SleeveView(std::shared_ptr<SleeveFolder> root_folder, const std::string& path,
               ElementResolver resolver);
    ~SleeveView() = default;

    SleeveView(const SleeveView&) = delete;
    SleeveView& operator=(const SleeveView&) = delete;
    SleeveView(SleeveView&&) = default;
    SleeveView& operator=(SleeveView&&) = default;

    void UpdateView();
    void UpdateView(const std::string& new_path);

    std::vector<std::weak_ptr<SleeveElement>> ListChildren() const;
    size_t GetChildCount() const;

    const std::string& GetViewingPath() const;
    void SetViewingPath(const std::string& path);

    std::shared_ptr<SleeveFolder> GetViewingFolder() const;

    bool IsAtRoot() const;
    bool NavigateUp();

    std::shared_ptr<SleeveElement> GetChildAt(size_t index) const;

    void SetResolver(ElementResolver resolver);

private:
    void RefreshChildren();

    std::shared_ptr<SleeveFolder> viewing_node_;
    std::string viewing_path_;
    std::vector<std::weak_ptr<SleeveElement>> children_;
    ElementResolver resolver_;
};

} // namespace alcedo

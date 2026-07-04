#include "sleeve_view.h"

#include <android/log.h>

#define LOG_TAG "AlcedoSleeveView"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

SleeveView::SleeveView(std::shared_ptr<SleeveFolder> root_folder, const std::string& path)
    : viewing_node_(std::move(root_folder)), viewing_path_(path), resolver_(nullptr) {
    if (viewing_node_) {
        RefreshChildren();
        LOGI("SleeveView created: path=%s children=%zu",
             viewing_path_.c_str(), children_.size());
    } else {
        LOGW("SleeveView created with null root folder");
    }
}

SleeveView::SleeveView(std::shared_ptr<SleeveFolder> root_folder, const std::string& path,
                       ElementResolver resolver)
    : viewing_node_(std::move(root_folder)), viewing_path_(path), resolver_(std::move(resolver)) {
    if (viewing_node_) {
        RefreshChildren();
        LOGI("SleeveView created with resolver: path=%s children=%zu",
             viewing_path_.c_str(), children_.size());
    } else {
        LOGW("SleeveView created with null root folder");
    }
}

void SleeveView::UpdateView() {
    RefreshChildren();
    LOGI("View updated: path=%s children=%zu",
         viewing_path_.c_str(), children_.size());
}

void SleeveView::UpdateView(const std::string& new_path) {
    viewing_path_ = new_path;
    RefreshChildren();
    LOGI("View updated to new path=%s children=%zu",
         viewing_path_.c_str(), children_.size());
}

std::vector<std::weak_ptr<SleeveElement>> SleeveView::ListChildren() const {
    return children_;
}

size_t SleeveView::GetChildCount() const {
    return children_.size();
}

const std::string& SleeveView::GetViewingPath() const {
    return viewing_path_;
}

void SleeveView::SetViewingPath(const std::string& path) {
    viewing_path_ = path;
    RefreshChildren();
}

std::shared_ptr<SleeveFolder> SleeveView::GetViewingFolder() const {
    return viewing_node_;
}

bool SleeveView::IsAtRoot() const {
    if (!viewing_node_) {
        return true;
    }
    return viewing_node_->parent_id == 0;
}

bool SleeveView::NavigateUp() {
    if (!viewing_node_ || viewing_node_->parent_id == 0) {
        LOGW("NavigateUp: already at root or no viewing node");
        return false;
    }

    // Trim the last segment from the path
    if (!viewing_path_.empty() && viewing_path_ != "/") {
        size_t last_sep = viewing_path_.find_last_of('/');
        if (last_sep == 0) {
            viewing_path_ = "/";
        } else if (last_sep != std::string::npos) {
            viewing_path_ = viewing_path_.substr(0, last_sep);
        }
    }

    // Resolve the parent folder using the resolver if available
    if (resolver_ && viewing_node_->parent_id != 0) {
        auto parent_elem = resolver_(viewing_node_->parent_id);
        if (parent_elem && parent_elem->IsFolder()) {
            viewing_node_ = std::static_pointer_cast<SleeveFolder>(parent_elem);
            RefreshChildren();
            LOGI("NavigateUp: resolved parent, path=%s children=%zu",
                 viewing_path_.c_str(), children_.size());
            return true;
        }
    }

    // Without a resolver, we cannot resolve the parent shared_ptr
    viewing_node_ = nullptr;
    children_.clear();

    LOGI("NavigateUp: path is now %s (no resolver, viewing_node cleared)",
         viewing_path_.c_str());
    return true;
}

std::shared_ptr<SleeveElement> SleeveView::GetChildAt(size_t index) const {
    if (index >= children_.size()) {
        LOGW("GetChildAt: index %zu out of range (size=%zu)", index, children_.size());
        return nullptr;
    }
    return children_[index].lock();
}

void SleeveView::SetResolver(ElementResolver resolver) {
    resolver_ = std::move(resolver);
}

void SleeveView::RefreshChildren() {
    children_.clear();
    if (!viewing_node_) {
        return;
    }

    if (!resolver_) {
        // Without a resolver we cannot produce weak_ptrs from the id map.
        LOGW("RefreshChildren: no resolver set, children list will be empty");
        return;
    }

    for (const auto& [name, id] : viewing_node_->contents) {
        auto elem = resolver_(id);
        if (elem) {
            children_.push_back(elem);
        } else {
            LOGW("RefreshChildren: failed to resolve element id=%u name=%s",
                 id, name.c_str());
        }
    }

    LOGI("RefreshChildren: path=%s resolved %zu children",
         viewing_path_.c_str(), children_.size());
}

} // namespace alcedo

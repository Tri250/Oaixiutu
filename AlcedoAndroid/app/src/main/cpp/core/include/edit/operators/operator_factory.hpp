// AlcedoAndroid - operator factory (name/type -> concrete operator). Header-only.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "edit/operators/op_base.hpp"
#include "json.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

// Registry of operator constructors keyed by OperatorType and script name.
class OperatorFactory {
 public:
  using Constructor = std::function<std::shared_ptr<IOperatorBase>()>;

  static OperatorFactory& Instance() {
    static OperatorFactory instance;
    return instance;
  }

  void Register(OperatorType type, std::string script_name, Constructor ctor) {
    by_type_[type] = std::move(ctor);
    name_to_type_[script_name] = type;
    type_to_name_[type] = std::move(script_name);
  }

  std::shared_ptr<IOperatorBase> Create(OperatorType type) const {
    auto it = by_type_.find(type);
    if (it == by_type_.end()) {
      ALOGW("OperatorFactory: no operator registered for type %d", static_cast<int>(type));
      return nullptr;
    }
    return it->second();
  }

  std::shared_ptr<IOperatorBase> CreateByScriptName(const std::string& name) const {
    auto it = name_to_type_.find(name);
    if (it == name_to_type_.end()) return nullptr;
    return Create(it->second);
  }

  std::optional<OperatorType> TypeFromScriptName(const std::string& name) const {
    auto it = name_to_type_.find(name);
    if (it == name_to_type_.end()) return std::nullopt;
    return it->second;
  }

  std::string ScriptNameFromType(OperatorType type) const {
    auto it = type_to_name_.find(type);
    return it == type_to_name_.end() ? "unknown" : it->second;
  }

  std::vector<OperatorType> RegisteredTypes() const {
    std::vector<OperatorType> out;
    out.reserve(by_type_.size());
    for (const auto& kv : by_type_) out.push_back(kv.first);
    return out;
  }

 private:
  OperatorFactory() = default;
  std::unordered_map<OperatorType, Constructor> by_type_;
  std::unordered_map<std::string, OperatorType> name_to_type_;
  std::unordered_map<OperatorType, std::string> type_to_name_;
};

// RAII helper used by operator_registration.cpp.
struct OperatorRegistrar {
  OperatorRegistrar(OperatorType type, std::string script_name,
                    OperatorFactory::Constructor ctor) {
    OperatorFactory::Instance().Register(type, std::move(script_name), std::move(ctor));
  }
};

}  // namespace alcedo

#pragma once

#include "operator_base.h"
#include <functional>
#include <memory>
#include <unordered_map>

namespace alcedo {

class OperatorFactory {
public:
    using CreatorFunc = std::function<std::unique_ptr<IOperatorBase>()>;

    static OperatorFactory& Instance();

    bool Register(OperatorType type, CreatorFunc creator);
    std::unique_ptr<IOperatorBase> Create(OperatorType type) const;

private:
    OperatorFactory() = default;
    std::unordered_map<OperatorType, CreatorFunc> creators_;
};

template<typename T>
class OperatorRegistrar {
public:
    explicit OperatorRegistrar(OperatorType type) {
        OperatorFactory::Instance().Register(type, []() -> std::unique_ptr<IOperatorBase> {
            return std::make_unique<T>();
        });
    }
};

#define REGISTER_OPERATOR(ClassName, OpType) \
    static ::alcedo::OperatorRegistrar<ClassName> _registrar_##ClassName(OpType);

} // namespace alcedo

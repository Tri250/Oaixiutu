#include "operator_factory.h"

namespace alcedo {

OperatorFactory& OperatorFactory::Instance() {
    static OperatorFactory instance;
    return instance;
}

bool OperatorFactory::Register(OperatorType type, CreatorFunc creator) {
    creators_[type] = std::move(creator);
    return true;
}

std::unique_ptr<IOperatorBase> OperatorFactory::Create(OperatorType type) const {
    auto it = creators_.find(type);
    if (it != creators_.end()) {
        return it->second();
    }
    return nullptr;
}

} // namespace alcedo

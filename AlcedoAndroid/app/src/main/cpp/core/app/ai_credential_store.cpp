// AlcedoAndroid - AiCredentialStore implementation.
// In-memory AI API key store (persisted by the JNI/settings layer).
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

namespace alcedo {

void AiCredentialStore::SetCredential(const std::string& provider_id, const std::string& api_key) {
  credentials_[provider_id] = api_key;
}

auto AiCredentialStore::GetCredential(const std::string& provider_id) const
    -> std::optional<std::string> {
  auto it = credentials_.find(provider_id);
  if (it == credentials_.end()) return std::nullopt;
  return it->second;
}

void AiCredentialStore::RemoveCredential(const std::string& provider_id) {
  credentials_.erase(provider_id);
}

}  // namespace alcedo

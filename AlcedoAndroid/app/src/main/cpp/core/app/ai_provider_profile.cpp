// AlcedoAndroid - AiProviderProfile implementation.
// Stores AI provider configuration (base URL + model id).
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

namespace alcedo {

void AiProviderProfile::SetProfile(const std::string& provider_id, const std::string& base_url,
                                   const std::string& model_id) {
  profiles_[provider_id] = {base_url, model_id};
}

auto AiProviderProfile::GetProfile(const std::string& provider_id) const
    -> std::optional<std::pair<std::string, std::string>> {
  auto it = profiles_.find(provider_id);
  if (it == profiles_.end()) return std::nullopt;
  return std::make_pair(it->second.base_url, it->second.model_id);
}

}  // namespace alcedo
